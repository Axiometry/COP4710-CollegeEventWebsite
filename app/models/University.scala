package models

case class University(id: Long, name: String, fullName: String, locationCoords: String, description: String, population: Long) {
  override def equals(o:Any) = o match {
    case u: University if id == u.id => true
    case _ => false
  }
  override def hashCode = id.##
}
