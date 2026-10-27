package com.bengu.springblog.controllers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@TestConfiguration(proxyBeanMethods = false)
@EnableWebSecurity
class WebSecurityTestConfig {
}
