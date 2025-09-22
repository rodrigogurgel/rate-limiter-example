package br.com.rodrigogurgel.ratelimiterexample.common.logger.decorator

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.StdDateFormat
import net.logstash.logback.decorate.JsonFactoryDecorator

class DateDecorator : JsonFactoryDecorator {
    override fun decorate(factory: JsonFactory): JsonFactory {
        (factory.codec as ObjectMapper).dateFormat = StdDateFormat.instance
        return factory
    }
}