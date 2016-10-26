package com.example

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.example.ScraperMasterActor._
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
 
class ScraperMasterActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  def this() = this(ActorSystem("MySpec"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "ScraperMasterActor" must {
    "send back ScraperStatus class" in {
      val scraper = system.actorOf(ScraperMasterActor.props)
      scraper ! GetStatus
      expectMsg(ScraperStatus(0,0))
    }

    "set domains" in {
      val scraper = system.actorOf(ScraperMasterActor.props)
      val domains = List("yandex.ru")
      scraper ! SetDomains(domains)
      scraper ! GetDomains
      expectMsg(PossibleDomains(domains))
    }

    "start scrape" in {
      val scraper = system.actorOf(ScraperMasterActor.props)
      val domains = List("yandex.ru","ebay.com","lenta.ru")
      scraper ! SetDomains(domains)
      scraper ! LinkToScrape("http://yandex.ru")
      scraper ! LinkToScrape("http://ebay.com")
      scraper ! LinkToScrape("http://lenta.ru")
      Thread.sleep(1400)
      scraper ! GetStatus
      Thread.sleep(2000)
      expectMsgPF() {
        case ScraperStatus(total, queue) if(total != queue && total > 0 && queue > 0) =>
        case obj => fail(s"Something went wrong $obj")
      }
    }

    "pause ScraperMasterActor" in {
      val scraper = system.actorOf(ScraperMasterActor.props)
      val domains = List("yandex.ru", "ebay.com", "lenta.ru")
      scraper ! SetDomains(domains)
      scraper ! LinkToScrape("http://yandex.ru")
      scraper ! LinkToScrape("http://ebay.com")
      scraper ! LinkToScrape("http://lenta.ru")
      Thread.sleep(1500)
      scraper ! Pause
      scraper ! GetStatus
      expectMsgPF() {
        case ScraperStatus(total, queue) if (total > 0 && queue > 0) =>
        case obj => fail(s"Something went wrong $obj")
      }
    }

    "pause and resume ScraperMasterActor" in {
      val scraper = system.actorOf(ScraperMasterActor.props)
      val domains = List("yandex.ru", "ebay.com", "lenta.ru")
      scraper ! SetDomains(domains)
      scraper ! LinkToScrape("http://yandex.ru")
      scraper ! LinkToScrape("http://ebay.com")
      scraper ! LinkToScrape("http://lenta.ru")
      Thread.sleep(1500)
      scraper ! Pause
      Thread.sleep(3000)
      scraper ! Resume
      Thread.sleep(3000)
      scraper ! GetStatus
      expectMsgPF() {
        case ScraperStatus(total, queue) if (total > 0 && queue == 0) =>
        case obj => fail(s"Something went wrong $obj")

      }
    }
  }

}
