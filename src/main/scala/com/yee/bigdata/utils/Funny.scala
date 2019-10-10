package com.yee.bigdata.utils

import java.io._
import java.net.{HttpURLConnection, URL}
import java.nio.charset.Charset

import com.itextpdf.text._
import com.itextpdf.text.pdf._
import com.yee.bigdata.utils.SMain.Button
import com.google.common.collect.MapMaker
import com.itextpdf.text.html.simpleparser.HTMLWorker
import com.itextpdf.tool.xml.XMLWorkerHelper
import javax.swing.text.html.HTMLWriter
import com.itextpdf.html2pdf.{ConverterProperties, HtmlConverter}
import com.itextpdf.layout.font.{FontInfo, FontProvider, FontSet}
import org.spark_project.jetty.util.preventers.Java2DLeakPreventer
import org.w3c.tidy.Tidy
import org.xhtmlrenderer.swing.Java2DRenderer
import org.xhtmlrenderer.util.FSImageWriter

import scala.io.Source
// import com.itextpdf.licensekey.LicenseKey


import scala.collection.JavaConverters._

class Funny(button:Button, name:String) extends AnyRef{

  def image(): Unit ={
    /*
    val f = new File("E:\\test\\b\\a.html")
    val renderer = new Java2DRenderer(f, 740, 795)
    val img = renderer.getImage()
    val writer = new FSImageWriter()
    writer.setWriteCompressionQuality(0.9f)
    writer.write(img, "E:\\\\tmp\\\\data\\\\a.png")
    */

    /*
    val in = new FileInputStream("E:\\test\\b\\b.html")
    val out = new FileOutputStream("E:\\\\tmp\\\\data\\\\a.png")
    val tidy = new Tidy()
    tidy.setInputEncoding("UTF-8")
    tidy.setOutputEncoding("UTF-8")
    tidy.setWraplen(0)
    tidy.setLiteralAttribs(true)
    tidy.parse(in,out)
    out.close()
    in.close()
    */

  }

  def pdf(path:String): Unit ={
    /*
    val reader = new PdfReader(input)
    reader.getInfo.asScala.foreach(f => {println(f._1 + " | " + f._2)})
    println("file length:" + reader.getFileLength)
    println("pages: " + reader.getNumberOfPages)
    val page = reader.getPageN(3)

    val input = "E:\\test\\b\\Spark SQL 之 Join 实现 _ 守护之鲨.html"
    val output = "E:\\tmp\\data\\a.pdf"
    val document = new Document(PageSize.LETTER)
    val writer = PdfWriter.getInstance(document,new FileOutputStream(output))
    document.open()
    XMLWorkerHelper.getInstance().parseXHtml(writer,document,new FileInputStream(input),Charset.forName("UTF-8"))
    document.close()
    */

    val props = new ConverterProperties()
    props.setCharset("UTF-8")
    props.setCreateAcroForm(true)

    val document = new Document(PageSize.A4,50,50,50,50)
    PdfWriter.getInstance(document,new FileOutputStream("E:\\\\tmp\\\\data\\\\d.pdf"))
    document.open()
    document.addAuthor("YuSheng")
    document.addTitle("spark-sql-join")
    document.addSubject("join")
    document.addKeywords("key words")
    document.addCreator("YuSheng")
    // val bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED)
    val bf = BaseFont.createFont("C:/windows/fonts/simsun.ttc,1", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
    val font = new Font(bf,14,Font.NORMAL)
    document.add(new Paragraph("Spark SQL 之 Join 实现",font))

    // val font2 = FontFactory.getFont(FontFactory.COURIER,14,Font.BOLD,new BaseColor(255,150,200))
    // document.add(new Paragraph("SparkSQL总体流程介绍",font2))
    document.close()

    val fontSet = new FontSet()
    fontSet.addFont("C:/windows/fonts/simsun.ttc,1")
    val fontProvider = new FontProvider(fontSet)
    props.setFontProvider(fontProvider)

    // val source = Source.fromURL("http://sharkdtu.com/posts/spark-sql-join.html")
    // source.getLines().foreach(println)

    //HtmlConverter.convertToPdf(reader,new FileOutputStream(""),props)

    val path = "https://localhost:8443/!/#bigdata/view/head/core/src/main/scala/org/apache/spark/util/collection/SizeTracker.scala"
    val url = new URL(path)
    val conn = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")



    // val in = conn.getInputStream
    val in = new FileInputStream("E:\\test\\b\\b.html")
    HtmlConverter.convertToPdf(in,new FileOutputStream("E:\\\\tmp\\\\data\\\\g.pdf"),props)
    // HtmlConverter.convertToPdf(new File("E:\\test\\b\\a.html"),new File("E:\\\\tmp\\\\data\\\\e.pdf"),props)

  }

  def doSome(): Unit ={
    println("do something...")
    // val names = Array("Wuhan","Ezhou","Jingmen","xianning","yichang","Wuhan","yichang")
    // Utils.duplicates(names).foreach(println)
    // println(Utils.generateUuidAsBase64())
    // Utils.takeOrdered(names.iterator,3).foreach(println)
    // val cMap = new MapMaker().weakKeys().makeMap[String,Long]()
    // cMap.put("",1L)
    // pdf("C:\\Users\\intest\\Desktop\\linuxcon2010_wheeler.pdf")
    // image()
  }
}
