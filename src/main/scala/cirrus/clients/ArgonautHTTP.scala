package cirrus.clients

import argonaut.Argonaut._
import argonaut._
import cirrus.internal.{Client, HTTPVerb, Response}
import cirrus.{`Accept`, `Content-Type`, `application/json`}

object ArgonautHTTP {
  
  case class GET[T](address: String)(implicit val decoder: DecodeJson[T], val client: Client)
    extends EmptyArgonautVerb[T]

  case class PUT[T](address: String)(implicit val decoder: DecodeJson[T], val client: Client)
    extends LoadedArgonautVerb[T]

  case class POST[T](address: String)(implicit val decoder: DecodeJson[T], val client: Client)
    extends LoadedArgonautVerb[T]

  case class DELETE[T](address: String)(implicit val decoder: DecodeJson[T], val client: Client)
    extends EmptyArgonautVerb[T]


  trait EmptyArgonautVerb[T] extends HTTPVerb {

    implicit val decoder: DecodeJson[T]

    def send = {

      implicit val ec = client.ec

      val verb = if (method == "GET") BasicHTTP GET address else BasicHTTP DELETE address

      verb withHeaders this.headers
      verb withHeader `Accept` -> `application/json`
      verb.send map asArgonaut[T]
    }
  }


  trait LoadedArgonautVerb[T] extends HTTPVerb {

    implicit val decoder: DecodeJson[T]

    def send[F: EncodeJson](payload: F) = {

      implicit val ec = client.ec

      val content = payload.asJson.nospaces

      val verb = if (method == "POST") BasicHTTP POST address else BasicHTTP PUT address

      verb withHeaders this.headers
      verb withHeader `Content-Type` -> `application/json` withHeader `Accept` -> `application/json`
      verb send content map asArgonaut[T]
    }
  }


  case class ArgonautResponse[T: DecodeJson](statusCode: Int, headers: Map[String, String], rawBody: String)
    extends Response {

    import argonaut._
    import Argonaut._

    override type Content = Option[T]
    override lazy val body: Content = rawBody.decodeOption(implicitly[DecodeJson[T]])
  }
}
