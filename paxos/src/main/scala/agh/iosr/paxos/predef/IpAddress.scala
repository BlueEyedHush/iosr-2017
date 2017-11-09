package agh.iosr.paxos.predef

import java.net.InetSocketAddress
import java.util.regex.Pattern

import scala.util.{Failure, Try}

case object InvalidAddressFormat extends Exception

case class IpAddress(ip: String, port: Int)

object IpAddress {
  private val DG = "[0-9]{1,3}"
  private val IP_REGEX = s"$DG(\\.$DG){3}"
  private val PORT_REGEX = s"[0-9]{1,5}"
  private val REGEX = Pattern.compile(s"(?<ip>$IP_REGEX):(?<port>$PORT_REGEX)")

  def fromString(addressString: String): Try[IpAddress] = {
    val m = REGEX.matcher(addressString)
    if(m.matches()) {
      Try {
        val ipStr = m.group("ip")
        val portStr = m.group("port")
        IpAddress(ipStr, portStr.toInt)
      }
    } else {
      Failure(InvalidAddressFormat)
    }
  }

  def fromInetAddress(insa: InetSocketAddress): IpAddress = {
    val oh = insa.getHostName
    val hostname =
      if(oh.contains("localhost"))
        "127.0.0.1"
      else
        oh

    IpAddress(hostname, insa.getPort)
  }

  implicit class IpAddressExtension(val ip: IpAddress) extends AnyVal {
    def toInetAddress: InetSocketAddress = new InetSocketAddress(ip.ip, ip.port)
  }
}
