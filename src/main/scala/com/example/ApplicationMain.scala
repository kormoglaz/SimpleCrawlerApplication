package com.example

import java.io.File

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import scala.concurrent.duration._
import scala.io.StdIn
import akka.pattern.ask
import com.example.ScraperMasterActor._
import com.typesafe.config.ConfigFactory

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol

trait Akka {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
}

trait AkkaCommon extends Akka {
  implicit val askTimeout: Timeout = 3.seconds
  val scraperMasterActor = system.actorOf(ScraperMasterActor.props,"ScraperMasterActor")
}

trait ServiceRoutes extends AkkaCommon with JsonSupport {
  implicit val statusFormat = jsonFormat2(ScraperStatus)
  implicit val possibleDomainsFormat = jsonFormat1(PossibleDomains)

  val route =
    get {
      path("domains") {
        onSuccess(
          (scraperMasterActor ? GetDomains).mapTo[PossibleDomains].recover {
            case _ =>
              println(s"GetDomains error")
              PossibleDomains()
          }
        ){ domains => complete(domains)}
      } ~
      path("status") {
        onSuccess(
          (scraperMasterActor ? GetStatus).mapTo[ScraperStatus].recover {
            case _ =>
              println(s"ScraperStatus error")
              ScraperStatus(0,0)
          }
        ){ status => complete(status)}
      }
    } ~
    post {
      path("domains") {
        entity(as[PossibleDomains]){ posDomains =>
          scraperMasterActor ! SetDomains(posDomains.ls.distinct.filter(_.nonEmpty))
          complete("Set domains action has been sent")
        }
      } ~
      parameters('page.as[String]){ page =>
        scraperMasterActor ! LinkToScrape(page)
        complete("Scrape action has been sent")
      } ~
      path("pause") {
        scraperMasterActor ! Pause
        complete("Pause action has been sent")
      } ~
      path("resume") {
        scraperMasterActor ! Resume
        complete("Resume action has been sent")
      }
    }
}

object ApplicationMain extends ServiceRoutes with FileStorage {
  def main(args: Array[String]) {

    if (!file.exists) file.mkdir

    val bindingFuture = Http().bindAndHandle(route, "localhost", 9090)

    println("Push Enter to terminate")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

trait FileStorage {
  val config = ConfigFactory.parseFile(new File("src/main/resources/application.conf"))
  val path = config.getString("storage.path")
  val file = new File(path)
}
