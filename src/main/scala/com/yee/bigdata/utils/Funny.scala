package com.yee.bigdata.utils

import java.io.FileOutputStream
import com.itextpdf.text.Document
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfDictionary
import com.itextpdf.text.pdf.PdfImportedPage
import com.itextpdf.text.pdf.PdfName
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfRectangle
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.PdfWriter
import com.yee.bigdata.utils.Main.Button
import com.google.common.collect.MapMaker
import scala.collection.JavaConverters._

class Funny(button:Button, name:String) extends AnyRef{

  def pdf(input:String): Unit ={
    val reader = new PdfReader(input)
    reader.getInfo.asScala.foreach(f => {println(f._1 + " | " + f._2)})
    println("file length:" + reader.getFileLength)
    println("pages: " + reader.getNumberOfPages)
    val page = reader.getPageN(3)
    

  }

  def doSome(): Unit ={
    println("do something...")
    // val names = Array("Wuhan","Ezhou","Jingmen","xianning","yichang","Wuhan","yichang")
    // Utils.duplicates(names).foreach(println)
    // println(Utils.generateUuidAsBase64())
    // Utils.takeOrdered(names.iterator,3).foreach(println)
    // val cMap = new MapMaker().weakKeys().makeMap[String,Long]()
    // cMap.put("",1L)
    pdf("C:\\Users\\intest\\Desktop\\linuxcon2010_wheeler.pdf")
  }
}
