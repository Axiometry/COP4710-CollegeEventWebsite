package models

case class Rso(id: Long, name: String, owner: User, university: University, approved: Boolean) {
  override def equals(o:Any) = o match {
    case rso: Rso if id == rso.id => true
    case _ => false
  }
  override def hashCode = id.##
}

case class RsoMembership(rso: Rso, user: User, approved: Boolean) {
  override def equals(o:Any) = o match {
    case m: RsoMembership if rso.id == m.rso.id && user.id == m.user.id => true
    case _ => false
  }
  override def hashCode = (rso, user).##
}