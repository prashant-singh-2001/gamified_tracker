package com.tracker.activity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.contracts.event.ActivityLoggedEvent;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange activityExchange(@Value("${messaging.exchange}") String name) {
        return new TopicExchange(name);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonConverter(ObjectMapper mapper) {
        // Stamped this class's FQCN into __TypeId__, which meant the header named
        // com.tracker.activity.messaging.ActivityLoggedEvent — a type the consumer never had.
        // return new Jackson2JsonMessageConverter(mapper);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        // Stamp the stable logical id instead, so moving the record between packages never
        // changes the wire header again.
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setIdClassMapping(Map.of(ActivityLoggedEvent.TYPE_ID, ActivityLoggedEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(conv);
        return template;
    }
}