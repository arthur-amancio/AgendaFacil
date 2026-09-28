package br.com.agendafacilpro.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class RuntimeEnvironmentConfiguration {

    @Bean
    static BeanFactoryPostProcessor runtimeEnvironmentValidator(Environment environment) {
        return beanFactory -> RuntimeEnvironmentValidator.validate(environment);
    }
}
