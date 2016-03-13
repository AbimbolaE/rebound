package com.godis.network.rebound


import com.godis.network.rebound.client.Basic.{POST, GET}

import scala.concurrent.Await

/**
  * Created by esurua01 on 11/03/2016.
  */
object BasicHTTPTest extends App {

  import Protocol._

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._


  val getResponse = Await.result(GET("http://192.168.0.8:9000/users") !, 10 seconds)

  val post = POST("http://192.168.0.8:9000/user")
  post header("Content-Type" -> "application/json")
  val postResponse = Await.result(post ! "{ \"user\": true }", 10 seconds)

  println(s"Content: ${getResponse.body}")
  println(s"Content: ${postResponse.body}")
}