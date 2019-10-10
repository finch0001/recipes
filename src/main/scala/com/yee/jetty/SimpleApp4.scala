package com.yee.jetty

object SimpleApp4 {
  def main(args: Array[String]): Unit = {
    val build = new HttpServer.Builder("hiveserver2")
      .setHost("localhost")
      .setPort(8983)
      .setMaxThreads(5)
      .setAllowedMethods("stacks")

    val server = build.build()
    server.start()
    System.out.println("sleep 5 min")
    Thread.sleep(300 * 1000)
  }
}
