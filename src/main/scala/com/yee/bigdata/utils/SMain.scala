package com.yee.bigdata.utils

import org.apache.spark.launcher.Main
import java.io.{FileInputStream, FileOutputStream}

import org.apache.spark.sql.SparkSession
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ResourceHandler

import scala.io.Source

object SMain {
  case class Button(id:Int,name:String)

  def startUp(): Unit ={
    val server = new  Server(8080)
    val handler = new ResourceHandler()
    handler.setResourceBase("E:\\资料\\open-sources\\apache-hive-3.1.1-src\\apache-hive-3.1.1-src")
    handler.setDirectoriesListed(true)
    server.setHandler(handler)
    server.start()
  }


  def main(args: Array[String]): Unit = {
    startUp()
    // val sparkSession = SparkSession.builder().appName("SMain").master("local[2]").getOrCreate()

    //println(Utils.createTempDir())
    // println(Utils.getProcessName())
    /*
    val body = (fileName:String,size:Int) => {
      val source = Source.fromFile(fileName)
      source.getLines().foreach(println)
    }
    */

    // Utils.tryLogNonFatalError(body("C:\\Users\\intest\\Desktop\\timechange.bat",123))

    // val content = Utils.offsetBytes("E:\\资料\\open-sources\\spark-rabbitmq-master\\spark-rabbitmq-master\\LICENSE.txt",12345,0,2000)
    // println(content)

    // val serializedBytes = Utils.serialize[Button](Button(1,"topButton"))
    // val button = Utils.deserialize[Button](serializedBytes)
    // println(button.toString)

    // val source = new FileInputStream("./logs/recipes.log")
    // val out = new FileOutputStream("./logs/b.out")
    // Utils.copyStream(source,out)

    // val j = Utils.createObject[Funny]("com.yee.bigdata.utils.Funny",Button(1,"topButton"),"j-1")
    // j.doSome()
  }

}
