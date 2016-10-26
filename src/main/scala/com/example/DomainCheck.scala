package com.example

trait DomainCheck {
  def getDomain(url: String) = url.toLowerCase.replaceAll("https://","").replaceAll("http://","").split("/").headOption.getOrElse("")
  def removeFirst(data: String) = if(data.take(2) == "//") data.drop(2) else if(data.headOption.getOrElse("") == '/') data.tail else data
  def removeLast(data: String) = if(data.lastOption.getOrElse("") == '/') data.dropRight(1) else data
}