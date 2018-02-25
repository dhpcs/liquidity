package com.dhpcs.liquidity.server.actor

import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.util.UUID

import akka.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import com.dhpcs.liquidity.actor.protocol.zonemonitor._
import com.dhpcs.liquidity.model.ZoneId
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

class ZoneMonitorActorSpec
    extends FreeSpec
    with ActorTestKit
    with BeforeAndAfterAll {

  "A ZoneMonitorActor" - {
    "provides a summary of the active zones" in {
      val zoneMonitor = spawn(ZoneMonitorActor.behavior, "zoneMonitor")
      val testProbe = TestProbe[Set[ActiveZoneSummary]]()
      val activeZoneSummary = ActiveZoneSummary(
        zoneId = ZoneId(UUID.randomUUID().toString),
        members = 0,
        accounts = 0,
        transactions = 0,
        metadata = None,
        connectedClients = Set.empty
      )
      zoneMonitor ! UpsertActiveZoneSummary(testProbe.ref, activeZoneSummary)
      zoneMonitor ! GetActiveZoneSummaries(testProbe.ref)
      testProbe.expectMessage(Set(activeZoneSummary))
    }
  }

  private[this] lazy val akkaRemotingPort = {
    val serverSocket = ServerSocketChannel.open().socket()
    serverSocket.bind(new InetSocketAddress("localhost", 0))
    val port = serverSocket.getLocalPort
    serverSocket.close()
    port
  }

  override def config: Config = ConfigFactory.parseString(s"""
       |akka {
       |  loglevel = "WARNING"
       |  actor.provider = "cluster"
       |  remote.netty.tcp {
       |    hostname = "localhost"
       |    port = $akkaRemotingPort
       |  }
       |  cluster {
       |    metrics.enabled = off
       |    seed-nodes = ["akka.tcp://$name@localhost:$akkaRemotingPort"]
       |    jmx.multi-mbeans-in-same-jvm = on
       |  }
       |}
     """.stripMargin)

  override protected def afterAll(): Unit =
    shutdownTestKit()

}
