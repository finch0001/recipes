package com.yee.bigdata.utils

import com.yee.bigdata.utils.Main.Button

class Funny(button:Button, name:String) extends AnyRef{
  def doSome(): Unit ={
    println("do something...")
    val names = Array("Wuhan","Ezhou","Jingmen","xianning","yichang","Wuhan","yichang")
    Utils.duplicates(names).foreach(println)
    println(Utils.generateUuidAsBase64())
  }
}
