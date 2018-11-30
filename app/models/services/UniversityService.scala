package models.services

import scala.concurrent.Future

import models._

trait UniversityService {
  def retrieve(): Future[Seq[University]]
  def retrieve(id: Long): Future[Option[University]]
  def save(university: University): Future[University]
  def delete(university: University): Future[Unit]
  def nextId(): Future[Long]
}
