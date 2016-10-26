package com.example

import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor.{Actor, ActorInitializationException, ActorKilledException, ActorLogging, ActorRef, OneForOneStrategy, Props}
import akka.routing.RoundRobinPool
import com.example.ImageDownloaderMasterActor.LinksToDownload
import com.example.ScraperMasterActor._
import org.jsoup.Jsoup

import scala.collection.JavaConversions._

class ScraperMasterActor extends Actor with ActorLogging with DomainCheck {
  //log.info(s"ScraperMasterActor has been started ${self.path}")
  val imageDownloader = context.actorOf(ImageDownloaderMasterActor.props,"ImageDownloaderMasterActor")

  private var possibleDomains = PossibleDomains()

  override val supervisorStrategy = OneForOneStrategy () {
    case _: ActorKilledException => Escalate
    case _: ActorInitializationException => Escalate
    case _ => Restart
  }

  val router: ActorRef =
    context.actorOf(RoundRobinPool(5).props(ScraperWorkerActor.props), "ScraperWorkerActor")

  def receive = {
    case msg: LinkToScrape =>
      if(possibleDomains.ls.map(getDomain).contains(getDomain(msg.url))){
        router.tell(msg,imageDownloader)
      }
    case SetDomains(ls) =>
      if(ls.nonEmpty) possibleDomains = possibleDomains.update(ls)
    case GetDomains => sender ! possibleDomains
    case msg @ GetStatus => imageDownloader forward msg
    case msg @ Pause => imageDownloader forward msg
    case msg @ Resume => imageDownloader forward msg
  }
}

object ScraperMasterActor {
  case class PossibleDomains(ls: List[String] = Nil){
    def update(domains: List[String]) = copy(ls = domains)
  }
  def props: Props = Props(new ScraperMasterActor)

  sealed trait ScraperMessages
  case object GetDomains extends ScraperMessages
  case class SetDomains(ls: List[String]) extends ScraperMessages
  case class LinkToScrape(url: String) extends ScraperMessages
  case object GetStatus extends ScraperMessages
  case class ScraperStatus(total: Int, queue: Int) extends ScraperMessages
  case object Pause extends ScraperMessages
  case object Resume extends ScraperMessages
}

class ScraperWorkerActor extends Actor with ActorLogging with DomainCheck with FileStorage {
  def receive = {
    case LinkToScrape(url) =>
      val links = getLinks(url)
      sender ! links
  }

  def getLinks(url: String): LinksToDownload = {
    try {
      val doc = Jsoup.connect(url).get()
      val images = doc.getElementsByTag("img")
      val linkList: List[String] =
        images.map(_.attr("src")).filter(_.nonEmpty).distinct.foldLeft(List[String]()) {(res,link) =>
          val newLink =
            if(link.contains("http://") || link.contains("https://")) link
            else if(link.contains(getDomain(url))) "http://" + removeFirst(link)
            else removeLast(url) + "/" + removeFirst(link)
          res :+ new java.net.URI(null, null, newLink, null).toASCIIString
        }
      LinksToDownload(linkList)
    } catch {
      case e: Exception =>
        log.error(e.getMessage)
        LinksToDownload(Nil)
    }
  }
}

object ScraperWorkerActor {
  def props = Props(new ScraperWorkerActor)
}
