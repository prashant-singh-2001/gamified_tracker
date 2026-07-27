package com.tracker.gamification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracker.contracts.event.ActivityLoggedEvent;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
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
    public DirectExchange deadLetterExchange(@Value("${messaging.dlx}") String name) {
        return new DirectExchange(name);
    }

    @Bean
    public Queue activityLoggedQueue(@Value("${messaging.queue}") String queue,
                                     @Value("${messaging.dlx}") String dlx,
                                     @Value("${messaging.dlq}") String dlqKey) {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqKey)
                .build();
    }

    @Bean
    public Queue deadLetterQueue(@Value("${messaging.dlq}") String dlq) {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding queueBinding(Queue activityLoggedQueue, TopicExchange activityExchange,
                                @Value("${messaging.routing-key}") String rk) {
        return BindingBuilder.bind(activityLoggedQueue).to(activityExchange).with(rk);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange,
                              @Value("${messaging.dlq}") String dlqKey) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(dlqKey);
    }

    // The producer now stamps the stable ActivityLoggedEvent.TYPE_ID, and the record itself lives
    // in the shared contracts module, so the header is finally resolvable on both sides.
    //
    // Precedence deliberately stays INFERRED, NOT TYPE_ID: a pre-upgrade activity-service still
    // publishes __TypeId__ = com.tracker.activity.messaging.ActivityLoggedEvent, which does not
    // exist here. INFERRED ignores the header entirely and deserializes into the @RabbitListener
    // parameter type, so a rolling deploy is safe in either order. Switch to TYPE_ID only once no
    // pre-upgrade producer can still be publishing.
    @Bean
    public Jackson2JsonMessageConverter jsonConverter(ObjectMapper mapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(Map.of(ActivityLoggedEvent.TYPE_ID, ActivityLoggedEvent.class));
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}