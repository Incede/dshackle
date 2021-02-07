/**
 * Copyright (c) 2021 EmeraldPay, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.monitoring

import com.sun.net.httpserver.HttpServer
import io.emeraldpay.dshackle.config.MainConfig
import io.emeraldpay.dshackle.config.MonitoringConfig
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.config.MeterFilter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import io.micrometer.prometheus.PrometheusConfig

import io.micrometer.prometheus.PrometheusMeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import javax.annotation.PostConstruct


@Service
class MonitoringSetup(
        @Autowired private val monitoringConfig: MonitoringConfig
) {

    companion object {
        private val log = LoggerFactory.getLogger(MonitoringSetup::class.java)
    }

    @PostConstruct
    fun setup() {
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Metrics.globalRegistry.add(prometheusRegistry)
        Metrics.globalRegistry.config().meterFilter(object: MeterFilter {
            override fun map(id: Meter.Id): Meter.Id {
                return id.withName("dshackle." + id.name)
            }
        })

        if (monitoringConfig.prometheus.enabled) {
            // use standard JVM server with a single thread blocking processing
            // prometheus is a single thread periodic call, no reason to setup anything complex
            try {
                log.info("Run Prometheus metrics on ${monitoringConfig.prometheus.host}:${monitoringConfig.prometheus.port}${monitoringConfig.prometheus.path}")
                val server = HttpServer.create(InetSocketAddress(monitoringConfig.prometheus.host, monitoringConfig.prometheus.port), 0);
                server.createContext(monitoringConfig.prometheus.path) { httpExchange ->
                    val response = prometheusRegistry.scrape()
                    httpExchange.sendResponseHeaders(200, response.toByteArray().size.toLong());
                    httpExchange.responseBody.use { os ->
                        os.write(response.toByteArray())
                    }
                }
                Thread(server::start).start();
            } catch (e: IOException) {
                log.error("Failed to start Prometheus Server", e)
            }
        }
    }
}