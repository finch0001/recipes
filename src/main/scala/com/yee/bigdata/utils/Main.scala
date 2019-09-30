package com.yee.bigdata.utils

import java.io.{FileInputStream, FileOutputStream}

import scala.io.Source

object Main {
  case class Button(id:Int,name:String)

  def main(args: Array[String]): Unit = {
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

    val j = Utils.createObject[Funny]("com.yee.bigdata.utils.Funny",Button(1,"topButton"),"j-1")
    j.doSome()
  }

}
