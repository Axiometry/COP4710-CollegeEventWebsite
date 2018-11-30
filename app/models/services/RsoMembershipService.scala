package models.services

import models._

import scala.concurrent.Future

trait RsoMembershipService {
  def retrieve(rso: Rso): Future[Seq[RsoMembership]]
  def retrieve(user: User): Future[Seq[RsoMembership]]
  def retrieve(rso: Rso, user: User): Future[Option[RsoMembership]]
  def save(membership: RsoMembership): Future[RsoMembership]
  def delete(membership: RsoMembership): Future[Unit]
}
