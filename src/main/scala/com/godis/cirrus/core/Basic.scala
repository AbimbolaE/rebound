package com.godis.cirrus.core

import java.net.{HttpURLConnection, URL}

import com.godis.cirrus.Defaults.ClientConfig._

import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsScalaMapConverter}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

case class BasicRequest(method: String, address: String, headers: List[(String, String)],
                        params: List[(String, String)], body: Option[String] = None) extends Request

case class BasicResponse(statusCode: Int, headers: Map[String, String], body: String) extends Response {
  override type Content = String
}

case class BasicClient(requestBodyCharset: String = charset, codec: Codec = codec,
                       defaultHeaders: List[(String, String)] = headers,
                       tweaks: List[(HttpURLConnection) => Unit] = tweaks)
                      (implicit val ec: ExecutionContext = ExecutionContext.global) extends Client {

  override def connect(request: Request): Future[Response] = {

    val promise = Promise[Response]()

    var connection: Option[HttpURLConnection] = None

    println(s"Client: $this")

    println(s"Request: $request")

    Future {

      // Open Connection
      val queryParams = if (request.params.nonEmpty) "?" + request.params.map(p => p._1 + "=" + p._2).mkString(",") else ""
      connection = Some(new URL(request.address + queryParams).openConnection().asInstanceOf[HttpURLConnection])

      // Set Request Method
      connection.foreach(_.setRequestMethod(request.method))

      // Apply Default Tweaks
      connection.foreach(c => tweaks.foreach(_(c)))

      // Apply Default Headers
      connection.foreach(c => defaultHeaders.foreach(h => c.setRequestProperty(h._1, h._2)))

      // Set Request Headers
      connection.foreach(c => request.headers.foreach(h => c.setRequestProperty(h._1, h._2)))

      // Set Request Body
      connection.foreach(c => request.body.foreach { b =>
        c.setDoOutput(true)
        c.setFixedLengthStreamingMode(b.length)
        c.getOutputStream.write(b.getBytes(requestBodyCharset))
      })

      // Generate Response
      val response = for {
        statusCode <- connection.map(_.getResponseCode)
        headers <- connection.map(_.getHeaderFields.asScala.map(e => (e._1, e._2.asScala.mkString(","))).toMap)
        if connection.get.getErrorStream == null
        body <- connection.map(c => Source.fromInputStream(c.getInputStream)(codec).mkString)
      } yield Success(BasicResponse(statusCode, headers, body))


      // Extract ErrorStream if necessary
      response orElse {

        val response = "\n" + Source.fromInputStream(connection.get.getErrorStream)(codec).mkString

        val error = Try(connection.get.getInputStream).recover { case ex => ex }.get.asInstanceOf[Exception]

        Some(Failure(new FailedRequest(response, error)))
      } foreach promise.complete

    } recover { case ex => promise.failure(ex)
    } andThen { case _ => connection.foreach(_.disconnect()) }

    promise.future
  }
}

object BasicClient {

  object Builder {

    private val _defaultHeaders = ListBuffer.empty[(String, String)]

    private var basicClient = BasicClient(defaultHeaders = _defaultHeaders.toList)

    def withDefaultHeaders(headers: List[(String, String)]) = {
      _defaultHeaders ++= headers
      this
    }

    def withDefaultHeader(header: (String, String)) = {
      _defaultHeaders += header
      this
    }

    def withExecutionContext(ec: ExecutionContext) = {
      basicClient = basicClient.copy()(ec)
      this
    }

    def withCodec(codec: Codec) = {
      basicClient = basicClient.copy(codec = codec)(basicClient.ec)
      this
    }

    def withRequestBodyCharset(charset: String) = {
      basicClient = basicClient.copy(requestBodyCharset = charset)(basicClient.ec)
      this
    }

    def withTweak(tweak: (HttpURLConnection) => Unit) = {
      basicClient = basicClient.copy(tweaks = basicClient.tweaks ::: tweak :: Nil)(basicClient.ec)
      this
    }

    def build() = basicClient
  }
}

case class FailedRequest(response: String, cause: Exception) extends RuntimeException(cause)