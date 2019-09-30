package com.yee.bigdata.utils

import java.io._
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.util.{Base64, Properties, UUID}
import java.util.concurrent.locks.{Lock, ReadWriteLock}
import java.util.zip.GZIPInputStream

import com.google.common.io.{ByteStreams, Files => GFiles}
import com.google.common.collect.{Ordering => GuavaOrdering}

import scala.util.Try
import scala.util.control.{ControlThrowable, NonFatal}
import org.apache.logging.log4j.LogManager

import scala.collection._
import scala.collection.mutable
import scala.io.Source
import scala.collection.JavaConverters._

object Utils {
  private val LOG = LogManager.getLogger(Utils.getClass)
  private val MAX_DIR_CREATION_ATTEMPTS: Int = 10

  /**
    * Delete a file or directory and its contents recursively.
    * Don't follow directories if they are symlinks.
    * Throws an exception if deletion is unsuccessful.
    */
  def deleteRecursively(file: File) {
    if (file != null) {
      try {
        if (file.isDirectory && !isSymlink(file)) {
          var savedIOException: IOException = null
          for (child <- listFilesSafely(file)) {
            try {
              deleteRecursively(child)
            } catch {
              // In case of multiple exceptions, only last one will be thrown
              case ioe: IOException => savedIOException = ioe
            }
          }
          if (savedIOException != null) {
            throw savedIOException
          }
        }
      } finally {
        if (!file.delete()) {
          // Delete can also fail if the file simply did not exist
          if (file.exists()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath)
          }
        }
      }
    }
  }

  /**
    * Check to see if file is a symbolic link.
    */
  def isSymlink(file: File): Boolean = {
    Files.isSymbolicLink(Paths.get(file.toURI))
  }

  private def listFilesSafely(file: File): Seq[File] = {
    if (file.exists()) {
      val files = file.listFiles()
      if (files == null) {
        throw new IOException("Failed to list files for dir: " + file)
      }
      files
    } else {
      List()
    }
  }

  /**
    * Create a directory inside the given parent directory. The directory is guaranteed to be
    * newly created, and is not marked for automatic deletion.
    */
  def createDirectory(root: String, namePrefix: String = "spark"): File = {
    var attempts = 0
    val maxAttempts = MAX_DIR_CREATION_ATTEMPTS
    var dir: File = null
    while (dir == null) {
      attempts += 1
      if (attempts > maxAttempts) {
        throw new IOException("Failed to create a temp directory (under " + root + ") after " +
          maxAttempts + " attempts!")
      }
      try {
        dir = new File(root, namePrefix + "-" + UUID.randomUUID.toString)
        if (dir.exists() || !dir.mkdirs()) {
          dir = null
        }
      } catch { case e: SecurityException => dir = null; }
    }

    dir.getCanonicalFile
  }

  /**
    * Create a temporary directory inside the given parent directory. The directory will be
    * automatically deleted when the VM shuts down.
    */
  def createTempDir( root: String = System.getProperty("java.io.tmpdir"),
                     namePrefix: String = "spark"): File = {
    val dir = createDirectory(root, namePrefix)
    dir
  }

  private def copyRecursive(source: File, dest: File): Unit = {
    if (source.isDirectory) {
      if (!dest.mkdir()) {
        throw new IOException(s"Failed to create directory ${dest.getPath}")
      }
      val subfiles = source.listFiles()
      subfiles.foreach(f => copyRecursive(f, new File(dest, f.getName)))
    } else {
      Files.copy(source.toPath, dest.toPath)
    }
  }

  /**
    * Copy all data from an InputStream to an OutputStream. NIO way of file stream to file stream
    * copying is disabled by default unless explicitly set transferToEnabled as true,
    * the parameter transferToEnabled should be configured by spark.file.transferTo = [true|false].
    */
  def copyStream(in: InputStream,
                 out: OutputStream,
                 closeStreams: Boolean = false,
                 transferToEnabled: Boolean = false): Long =
  {
    var count = 0L
    tryWithSafeFinally {
      if (in.isInstanceOf[FileInputStream] && out.isInstanceOf[FileOutputStream]
        && transferToEnabled) {
        // When both streams are File stream, use transferTo to improve copy performance.
        val inChannel = in.asInstanceOf[FileInputStream].getChannel()
        val outChannel = out.asInstanceOf[FileOutputStream].getChannel()
        val initialPos = outChannel.position()
        val size = inChannel.size()

        // In case transferTo method transferred less data than we have required.
        while (count < size) {
          count += inChannel.transferTo(count, size - count, outChannel)
        }

        // Check the position after transferTo loop to see if it is in the right position and
        // give user information if not.
        // Position will not be increased to the expected length after calling transferTo in
        // kernel version 2.6.32, this issue can be seen in
        // https://bugs.openjdk.java.net/browse/JDK-7052359
        // This will lead to stream corruption issue when using sort-based shuffle (SPARK-3948).
        val finalPos = outChannel.position()
        assert(finalPos == initialPos + size,
          s"""
             |Current position $finalPos do not equal to expected position ${initialPos + size}
             |after transferTo, please check your kernel version to see if it is 2.6.32,
             |this is a kernel bug which will lead to unexpected behavior when using transferTo.
             |You can set spark.file.transferTo = false to disable this NIO feature.
           """.stripMargin)
      } else {
        val buf = new Array[Byte](8192)
        var n = 0
        while (n != -1) {
          n = in.read(buf)
          if (n != -1) {
            out.write(buf, 0, n)
            count += n
          }
        }
      }
      count
    } {
      if (closeStreams) {
        try {
          in.close()
        } finally {
          out.close()
        }
      }
    }
  }

  /**
    * Return the string to tell how long has passed in milliseconds.
    */
  def getUsedTimeMs(startTimeMs: Long): String = {
    " " + (System.currentTimeMillis - startTimeMs) + " ms"
  }

  /**
    * Returns the name of this JVM process. This is OS dependent but typically (OSX, Linux, Windows),
    * this is formatted as PID@hostname.
    */
  def getProcessName(): String = {
    ManagementFactory.getRuntimeMXBean().getName()
  }

  def tryWithResource[R <: Closeable, T](createResource: => R)(f: R => T): T = {
    val resource = createResource
    try f.apply(resource) finally resource.close()
  }

  /**
    * Return whether the specified file is a parent directory of the child file.
    */
  def isInDirectory(parent: File, child: File): Boolean = {
    if (child == null || parent == null) {
      return false
    }
    if (!child.exists() || !parent.exists() || !parent.isDirectory()) {
      return false
    }
    if (parent.equals(child)) {
      return true
    }
    isInDirectory(parent, child.getParentFile)
  }

  /** Executes the given block in a Try, logging any uncaught exceptions. */
  def tryLog[T](f: => T): Try[T] = {
    try {
      val res = f
      scala.util.Success(res)
    } catch {
      case ct: ControlThrowable => {
        throw ct
      }
      case t: Throwable => {
        LOG.error(s"Uncaught exception in thread ${Thread.currentThread().getName}", t)
        scala.util.Failure(t)
      }
    }
  }

  /**
    * Execute a block of code, then a finally block, but if exceptions happen in
    * the finally block, do not suppress the original exception.
    *
    * This is primarily an issue with `finally { out.close() }` blocks, where
    * close needs to be called to clean up `out`, but if an exception happened
    * in `out.write`, it's likely `out` may be corrupted and `out.close` will
    * fail as well. This would then suppress the original/likely more meaningful
    * exception from the original `out.write` call.
    */
  def tryWithSafeFinally[T](block: => T)(finallyBlock: => Unit): T = {
    var originalThrowable: Throwable = null
    try {
      block
    } catch {
      case t: Throwable => {
        // Purposefully not using NonFatal, because even fatal exceptions
        // we don't want to have our finallyBlock suppress
        originalThrowable = t
        throw originalThrowable
      }
    } finally {
      try {
        finallyBlock
      } catch {
        case t: Throwable =>
          if (originalThrowable != null) {
            originalThrowable.addSuppressed(t)
            LOG.warn(s"Suppressing exception in finally: " + t.getMessage, t)
            throw originalThrowable
          } else {
            throw t
          }
      }
    }
  }

  /**
    * Execute a block of code and call the failure callbacks in the catch block. If exceptions occur
    * in either the catch or the finally block, they are appended to the list of suppressed
    * exceptions in original exception which is then rethrown.
    *
    * This is primarily an issue with `catch { abort() }` or `finally { out.close() }` blocks,
    * where the abort/close needs to be called to clean up `out`, but if an exception happened
    * in `out.write`, it's likely `out` may be corrupted and `abort` or `out.close` will
    * fail as well. This would then suppress the original/likely more meaningful
    * exception from the original `out.write` call.
    */
  def tryWithSafeFinallyAndFailureCallbacks[T](block: => T)
                                              (catchBlock: => Unit = (), finallyBlock: => Unit = ()): T = {
    var originalThrowable: Throwable = null
    try {
      block
    } catch {
      case cause: Throwable => {
        // Purposefully not using NonFatal, because even fatal exceptions
        // we don't want to have our finallyBlock suppress
        originalThrowable = cause
        try {
          LOG.error("Aborting task", originalThrowable)
          // TaskContext.get().asInstanceOf[TaskContextImpl].markTaskFailed(originalThrowable)
          catchBlock
        } catch {
          case t: Throwable => {
            originalThrowable.addSuppressed(t)
            LOG.warn(s"Suppressing exception in catch: " + t.getMessage, t)
          }
        }
        throw originalThrowable
      }
    } finally {
      try {
        finallyBlock
      } catch {
        case t: Throwable =>
          if (originalThrowable != null) {
            originalThrowable.addSuppressed(t)
            LOG.warn(s"Suppressing exception in finally: " + t.getMessage, t)
            throw originalThrowable
          } else {
            throw t
          }
      }
    }
  }

  /** Executes the given block. Log non-fatal errors if any, and only throw fatal errors */
  def tryLogNonFatalError(block: => Unit) {
    try {
      block
    } catch {
      case NonFatal(t) =>
        LOG.error(s"Uncaught exception in thread ${Thread.currentThread().getName}", t)
    }
  }

  /**
    * Execute a block of code that returns a value, re-throwing any non-fatal uncaught
    * exceptions as IOException. This is used when implementing Externalizable and Serializable's
    * read and write methods, since Java's serializer will not report non-IOExceptions properly;
    * see SPARK-4080 for more context.
    */
  def tryOrIOException[T](block: => T): T = {
    try {
      block
    } catch {
      case e: IOException =>
        LOG.error("Exception encountered", e)
        throw e
      case NonFatal(e) =>
        LOG.error("Exception encountered", e)
        throw new IOException(e)
    }
  }

  /**
    * Return and start a daemon thread that processes the content of the input stream line by line.
    */
  def processStreamByLine( threadName: String,
                           inputStream: InputStream,
                           processLine: String => Unit): Thread = {
    val t = new Thread(threadName) {
      override def run() {
        for (line <- Source.fromInputStream(inputStream).getLines()) {
          processLine(line)
        }
      }
    }
    t.setDaemon(true)
    t.start()
    t
  }

  /**
    * Execute a command and return the process running the command.
    */
  def executeCommand( command: Seq[String],
                      workingDir: File = new File("."),
                      extraEnvironment: Map[String, String] = Map.empty,
                      redirectStderr: Boolean = true): Process = {
    val builder = new ProcessBuilder(command: _*).directory(workingDir)
    val environment = builder.environment()
    for ((key, value) <- extraEnvironment) {
      environment.put(key, value)
    }
    val process = builder.start()
    if (redirectStderr) {
      val threadName = "redirect stderr for command " + command(0)
      def log(s: String): Unit = LOG.info(s)
      processStreamByLine(threadName, process.getErrorStream, log)
    }
    process
  }

  /**
    * Execute a command and get its output, throwing an exception if it yields a code other than 0.
    */
  def executeAndGetOutput( command: Seq[String],
                           workingDir: File = new File("."),
                           extraEnvironment: Map[String, String] = Map.empty,
                           redirectStderr: Boolean = true): String = {
    val process = executeCommand(command, workingDir, extraEnvironment, redirectStderr)
    val output = new StringBuilder
    val threadName = "read stdout for " + command(0)
    def appendToOutput(s: String): Unit = output.append(s).append("\n")
    val stdoutThread = processStreamByLine(threadName, process.getInputStream, appendToOutput)
    val exitCode = process.waitFor()
    stdoutThread.join()   // Wait for it to finish reading output
    if (exitCode != 0) {
      LOG.error(s"Process $command exited with code $exitCode: $output")
      throw new RuntimeException(s"Process $command exited with code $exitCode")
    }
    output.toString
  }

  /** Return a string containing part of a file from byte 'start' to 'end'. */
  def offsetBytes(path: String, length: Long, start: Long, end: Long): String = {
    val file = new File(path)
    val effectiveEnd = math.min(length, end)
    val effectiveStart = math.max(0, start)
    val buff = new Array[Byte]((effectiveEnd-effectiveStart).toInt)
    val stream = if (path.endsWith(".gz")) {
      new GZIPInputStream(new FileInputStream(file))
    } else {
      new FileInputStream(file)
    }

    try {
      ByteStreams.skipFully(stream, effectiveStart)
      ByteStreams.readFully(stream, buff)
    } finally {
      stream.close()
    }
    Source.fromBytes(buff).mkString
  }

  /** Serialize an object using Java serialization */
  def serialize[T](o: T): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bos)
    oos.writeObject(o)
    oos.close()
    bos.toByteArray
  }

  /** Deserialize an object using Java serialization */
  def deserialize[T](bytes: Array[Byte]): T = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis)
    ois.readObject.asInstanceOf[T]
  }

  /** Deserialize an object using Java serialization and the given ClassLoader */
  def deserialize[T](bytes: Array[Byte], loader: ClassLoader): T = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis) {
      override def resolveClass(desc: ObjectStreamClass): Class[_] = {
        // scalastyle:off classforname
        Class.forName(desc.getName, false, loader)
        // scalastyle:on classforname
      }
    }
    ois.readObject.asInstanceOf[T]
  }

  /**
    * Download `in` to `tempFile`, then move it to `destFile`.
    *
    * If `destFile` already exists:
    *   - no-op if its contents equal those of `sourceFile`,
    *   - throw an exception if `fileOverwrite` is false,
    *   - attempt to overwrite it otherwise.
    *
    * @param url URL that `sourceFile` originated from, for logging purposes.
    * @param in InputStream to download.
    * @param destFile File path to move `tempFile` to.
    * @param fileOverwrite Whether to delete/overwrite an existing `destFile` that does not match
    *                      `sourceFile`
    */
  private def downloadFile( url: String,
                            in: InputStream,
                            destFile: File,
                            fileOverwrite: Boolean): Unit = {
    val tempFile = File.createTempFile("fetchFileTemp", null,
      new File(destFile.getParentFile.getAbsolutePath))
    LOG.info(s"Fetching $url to $tempFile")

    try {
      val out = new FileOutputStream(tempFile)
      Utils.copyStream(in, out, closeStreams = true)
      copyFile(url, tempFile, destFile, fileOverwrite, removeSourceFile = true)
    } finally {
      // Catch-all for the couple of cases where for some reason we didn't move `tempFile` to
      // `destFile`.
      if (tempFile.exists()) {
        tempFile.delete()
      }
    }
  }

  /**
    * Copy `sourceFile` to `destFile`.
    *
    * If `destFile` already exists:
    *   - no-op if its contents equal those of `sourceFile`,
    *   - throw an exception if `fileOverwrite` is false,
    *   - attempt to overwrite it otherwise.
    *
    * @param url URL that `sourceFile` originated from, for logging purposes.
    * @param sourceFile File path to copy/move from.
    * @param destFile File path to copy/move to.
    * @param fileOverwrite Whether to delete/overwrite an existing `destFile` that does not match
    *                      `sourceFile`
    * @param removeSourceFile Whether to remove `sourceFile` after / as part of moving/copying it to
    *                         `destFile`.
    */
  private def copyFile( url: String,
                        sourceFile: File,
                        destFile: File,
                        fileOverwrite: Boolean,
                        removeSourceFile: Boolean = false): Unit = {

    if (destFile.exists) {
      if (!filesEqualRecursive(sourceFile, destFile)) {
        if (fileOverwrite) {
          LOG.info(
            s"File $destFile exists and does not match contents of $url, replacing it with $url"
          )
          if (!destFile.delete()) {
            throw new RuntimeException(
              "Failed to delete %s while attempting to overwrite it with %s".format(
                destFile.getAbsolutePath,
                sourceFile.getAbsolutePath
              )
            )
          }
        } else {
          throw new RuntimeException(
            s"File $destFile exists and does not match contents of $url")
        }
      } else {
        // Do nothing if the file contents are the same, i.e. this file has been copied
        // previously.
        LOG.info(
          "%s has been previously copied to %s".format(
            sourceFile.getAbsolutePath,
            destFile.getAbsolutePath
          )
        )
        return
      }
    }

    // The file does not exist in the target directory. Copy or move it there.
    if (removeSourceFile) {
      Files.move(sourceFile.toPath, destFile.toPath)
    } else {
      LOG.info(s"Copying ${sourceFile.getAbsolutePath} to ${destFile.getAbsolutePath}")
      copyRecursive(sourceFile, destFile)
    }
  }

  private def filesEqualRecursive(file1: File, file2: File): Boolean = {
    if (file1.isDirectory && file2.isDirectory) {
      val subfiles1 = file1.listFiles()
      val subfiles2 = file2.listFiles()
      if (subfiles1.size != subfiles2.size) {
        return false
      }
      subfiles1.sortBy(_.getName).zip(subfiles2.sortBy(_.getName)).forall {
        case (f1, f2) => filesEqualRecursive(f1, f2)
      }
    } else if (file1.isFile && file2.isFile) {
      GFiles.equal(file1, file2)
    } else {
      false
    }
  }

  /**
    * Invokes every function in `all` even if one or more functions throws an exception.
    *
    * If any of the functions throws an exception, the first one will be rethrown at the end with subsequent exceptions
    * added as suppressed exceptions.
    */
  // Note that this is a generalised version of `Utils.closeAll`. We could potentially make it more general by
  // changing the signature to `def tryAll[R](all: Seq[() => R]): Seq[R]`
  def tryAll(all: Seq[() => Unit]): Unit = {
    var exception: Throwable = null
    all.foreach { element =>
      try element.apply()
      catch {
        case e: Throwable =>
          if (exception != null)
            exception.addSuppressed(e)
          else
            exception = e
      }
    }
    if (exception != null)
      throw exception
  }

  /**
    * Wrap the given function in a java.lang.Runnable
    * @param fun A function
    * @return A Runnable that just executes the function
    */
  def runnable(fun: => Unit): Runnable =
    new Runnable {
      def run() = fun
    }

  /**
    * Create a thread
    *
    * @param name The name of the thread
    * @param daemon Whether the thread should block JVM shutdown
    * @param fun The function to execute in the thread
    * @return The unstarted thread
    */
  def newThread(name: String, daemon: Boolean)(fun: => Unit): Thread =
    new AppThread(name, runnable(fun), daemon)

  /**
    * Create an instance of the class with the given class name
    */
  def createObject[T <: AnyRef](className: String, args: AnyRef*): T = {
    val klass = Class.forName(className, true, JUtils.getContextOrAppClassLoader()).asInstanceOf[Class[T]]
    val constructor = klass.getConstructor(args.map(_.getClass): _*)
    constructor.newInstance(args: _*)
  }

  /**
    * Parse a comma separated string into a sequence of strings.
    * Whitespace surrounding the comma will be removed.
    */
  def parseCsvList(csvList: String): Seq[String] = {
    if(csvList == null || csvList.isEmpty)
      Seq.empty[String]
    else {
      csvList.split("\\s*,\\s*").filter(v => !v.equals(""))
    }
  }

  /**
    * This method gets comma separated values which contains key,value pairs and returns a map of
    * key value pairs. the format of allCSVal is key1:val1, key2:val2 ....
    * Also supports strings with multiple ":" such as IpV6 addresses, taking the last occurrence
    * of the ":" in the pair as the split, eg a:b:c:val1, d:e:f:val2 => a:b:c -> val1, d:e:f -> val2
    */
  def parseCsvMap(str: String): Map[String, String] = {
    val map = new mutable.HashMap[String, String]
    if ("".equals(str))
      return map
    val keyVals = str.split("\\s*,\\s*").map(s => {
      val lio = s.lastIndexOf(":")
      (s.substring(0,lio).trim, s.substring(lio + 1).trim)
    })
    keyVals.toMap
  }

  /**
    * Create a circular (looping) iterator over a collection.
    * @param coll An iterable over the underlying collection.
    * @return A circular iterator over the collection.
    */
  def circularIterator[T](coll: Iterable[T]):Iterator[T] = {
    for (_ <- Iterator.continually(1); t <- coll) yield t
  }

  /**
    * Execute the given function inside the lock
    */
  def inLock[T](lock: Lock)(fun: => T): T = {
    lock.lock()
    try {
      fun
    } finally {
      lock.unlock()
    }
  }

  def inReadLock[T](lock: ReadWriteLock)(fun: => T): T = inLock[T](lock.readLock)(fun)

  def inWriteLock[T](lock: ReadWriteLock)(fun: => T): T = inLock[T](lock.writeLock)(fun)

  /**
    * Returns a list of duplicated items
    */
  def duplicates[T](s: Traversable[T]): Iterable[T] = {
    s.groupBy(identity)
      .map { case (k, l) => (k, l.size)}
      .filter { case (_, l) => l > 1 }
      .keys
  }

  def generateUuidAsBase64(): String = {
    val uuid = UUID.randomUUID()
    Base64.getUrlEncoder.withoutPadding.encodeToString(getBytesFromUuid(uuid))
  }

  def getBytesFromUuid(uuid: UUID): Array[Byte] = {
    // Extract bytes for uuid which is 128 bits (or 16 bytes) long.
    val uuidBytes = ByteBuffer.wrap(new Array[Byte](16))
    uuidBytes.putLong(uuid.getMostSignificantBits)
    uuidBytes.putLong(uuid.getLeastSignificantBits)
    uuidBytes.array
  }

  def propsWith(props: (String, String)*): Properties = {
    val properties = new Properties()
    props.foreach { case (k, v) => properties.put(k, v) }
    properties
  }

  /**
    * Atomic `getOrElseUpdate` for concurrent maps. This is optimized for the case where
    * keys often exist in the map, avoiding the need to create a new value. `createValue`
    * may be invoked more than once if multiple threads attempt to insert a key at the same
    * time, but the same inserted value will be returned to all threads.
    *
    * In Scala 2.12, `ConcurrentMap.getOrElse` has the same behaviour as this method, but that
    * is not the case in Scala 2.11. We can remove this method once we drop support for Scala
    * 2.11.
    */
  def atomicGetOrUpdate[K, V](map: concurrent.Map[K, V], key: K, createValue: => V): V = {
    map.get(key) match {
      case Some(value) => value
      case None =>
        val value = createValue
        map.putIfAbsent(key, value).getOrElse(value)
    }
  }

  /**
    * Returns the first K elements from the input as defined by the specified implicit Ordering[T]
    * and maintains the ordering.
    */
  def takeOrdered[T](input: Iterator[T], num: Int)(implicit ord: Ordering[T]): Iterator[T] = {
    val ordering = new GuavaOrdering[T] {
      override def compare(l: T, r: T): Int = ord.compare(l, r)
    }
    ordering.leastOf(input.asJava, num).iterator.asScala
  }

}
