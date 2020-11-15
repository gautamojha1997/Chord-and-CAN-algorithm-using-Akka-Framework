object UtilityTest {
  def md5(s: String, bitsNumber: Int): Int = {
    val md = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
    val bin = BigInt(md, 16).toString(2).take(bitsNumber)
    Integer.parseInt(bin, 2)
  }
}
