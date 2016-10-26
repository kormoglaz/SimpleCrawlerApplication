package com.example

import java.io.File
import java.util.{Date, UUID}

import akka.Done
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorInitializationException, ActorKilledException, ActorLogging, ActorRef, ActorSystem, Kill, OneForOneStrategy, Props, Terminated}
import akka.http.javadsl.model.headers.ContentDisposition
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.routing.RoundRobinPool
import akka.stream.scaladsl.{FileIO, Sink, Source}
import com.example.ImageDownloaderMasterActor.{LinksToDownload, OneLink}
import akka.pattern.pipe
import com.example.ScraperMasterActor.{GetStatus, Pause, Resume, ScraperStatus}

import scala.concurrent.Future
import scala.util.Try

class ImageDownloaderMasterActor extends Actor with ActorLogging {
  //log.info(s"ImageDownloaderMasterActor has been started ${self.path}")
  override val supervisorStrategy = OneForOneStrategy () {
    case _: ActorInitializationException => Escalate
    case _ => Restart
  }

  val router: ActorRef =
    context.actorOf(RoundRobinPool(5).props(Props(new ImageDownloaderWorkerActor())), "ImageDownloaderWorkerActor")

  private var total = List[String]()
  private var passed = List[String]()

  def receive = {
    case LinksToDownload(ls) =>
      total = total ++ ls
      ls.foreach { link =>
        router.tell(OneLink(link),self)
      }
    case OneLink(link) =>
      passed = link :: passed
    case GetStatus =>
      log.info(s"Status is ${ScraperStatus(total.size,total.size - passed.size)}")
      sender ! ScraperStatus(total.size,total.size - passed.size)
    case Pause =>
      context.children.foreach(_ ! Kill)
      context.become {
        case GetStatus =>
          log.info(s"Status is ${ScraperStatus(total.size,total.size - passed.size)}")
          sender ! ScraperStatus(total.size,total.size - passed.size)
        case Resume =>
          total.filter(!passed.contains(_)).foreach { link =>
            router.tell(OneLink(link),self)
          }
          context.unbecome()
      }
  }
}

object ImageDownloaderMasterActor {
  def props = Props(new ImageDownloaderMasterActor)

  case class LinksToDownload(ls: List[String])
  case class OneLink(url: String)
}

class ImageDownloaderWorkerActor extends Actor with ActorLogging with FileStorage with DomainCheck with Akka {
  log.info("ImageDownloaderWorker has started")
  val dir = new File(path)

  def receive = {
    case msg @ OneLink(url) =>
      val f = download(Uri(url),dir,url).recover {
        case e: Exception =>
          log.error(e.getMessage)
      }.map(_ => msg)
      pipe(f) to sender
  }

  def destinationFile(downloadDir: File, response: HttpResponse, url: String): File = {
    val tempFileName = url.split("/").lastOption.getOrElse(UUID.randomUUID().toString)
    val fileName = getDomain(url) + "-" + tempFileName
    val file = new File(downloadDir, fileName)
    file.createNewFile()
    file
  }

  def writeFile(downloadDir : File, domain: String)(httpResponse : HttpResponse) = {
    val file = destinationFile(downloadDir, httpResponse, domain)
    httpResponse.entity.dataBytes.runWith(FileIO.toPath(file.toPath))
  }

  def responseOrFail[T](in: (Try[HttpResponse], T)): (HttpResponse, T) = in match {
    case (responseTry, context) => (responseTry.get, context)
  }

  def download(uri: Uri, downloadDir: File, domain: String): Future[Done] = {
    val request = HttpRequest(uri = uri)
    val source = Source.single((request, ()))
    val requestResponseFlow = Http().superPool[Unit]()

    source.via(requestResponseFlow)
      .map(responseOrFail)
      .map(_._1)
      .mapAsyncUnordered(10)(writeFile(downloadDir,domain))
      .runWith(Sink.ignore)
  }
}

object ImageDownloaderWorkerActor {
  def props = Props(new ImageDownloaderWorkerActor)
}