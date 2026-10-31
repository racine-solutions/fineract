/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.selfservice.security.starter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.cache.service.CacheWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractRequestContextHolder;
import org.apache.fineract.infrastructure.core.filters.CorrelationHeaderFilter;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreFilter;
import org.apache.fineract.infrastructure.core.filters.IdempotencyStoreHelper;
import org.apache.fineract.infrastructure.core.filters.RequestResponseFilter;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.MDCWrapper;
import org.apache.fineract.infrastructure.instancemode.filter.FineractInstanceModeApiFilter;
import org.apache.fineract.infrastructure.jobs.filter.LoanCOBApiFilter;
import org.apache.fineract.infrastructure.jobs.filter.LoanCOBFilterHelper;
import org.apache.fineract.infrastructure.security.data.PlatformRequestLog;
import org.apache.fineract.infrastructure.security.filter.TwoFactorAuthenticationFilter;
import org.apache.fineract.infrastructure.security.service.AuthTenantDetailsService;
import org.apache.fineract.infrastructure.security.service.TwoFactorService;
import org.apache.fineract.notification.service.UserNotificationService;
import org.apache.fineract.selfservice.security.filter.SelfServiceAuthenticationConverter;
import org.apache.fineract.selfservice.security.filter.SelfServiceBasicAuthenticationFilter;
import org.apache.fineract.selfservice.security.filter.SelfServiceEnablerSecurityFilter;
import org.apache.fineract.selfservice.security.service.SelfServiceAuthenticationTokenService;
import org.apache.fineract.selfservice.security.service.SelfServiceTokenAuthenticationProvider;
import org.apache.fineract.selfservice.security.service.SelfServiceUserAuthorizationManager;
import org.apache.fineract.selfservice.security.service.TenantAwareJpaPlatformSelfServiceUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Order(1) // Very important: Must have higher priority than main security config
public class SelfServiceSecurityConfiguration {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TenantAwareJpaPlatformSelfServiceUserDetailsService userDetailsService;
    @Autowired
    private FineractProperties fineractProperties;
    @Autowired
    private ToApiJsonSerializer<PlatformRequestLog> toApiJsonSerializer;
    @Autowired
    private ConfigurationDomainService configurationDomainService;
    @Autowired
    private CacheWritePlatformService cacheWritePlatformService;
    @Autowired
    private UserNotificationService userNotificationService;
    @Autowired
    private AuthTenantDetailsService basicAuthTenantDetailsService;
    @Autowired
    private BusinessDateReadPlatformService businessDateReadPlatformService;
    @Autowired
    private MDCWrapper mdcWrapper;
    @Autowired
    private FineractRequestContextHolder fineractRequestContextHolder;

    @Autowired(required = false)
    private LoanCOBFilterHelper loanCOBFilterHelper;

    @Autowired
    private IdempotencyStoreHelper idempotencyStoreHelper;

    @Autowired
    private SelfServiceAuthenticationTokenService tokenService;

    @Bean
    @Order(1)
    public SecurityFilterChain selfServiceSecurityFilterChain(HttpSecurity http) throws Exception {

        http
                // Apply only to self-service endpoints
                .securityMatchers(matchers -> matchers.requestMatchers(antMatcher("/api/v1/self/**"), antMatcher("/v1/self/**")))

                // Disable CSRF for public self-service APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session
                .sessionManagement(smc -> smc.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).addFilterBefore(
                        selfServiceEnablerSecurityFilter(), SecurityContextHolderFilter.class)
                .addFilterBefore(tenantAwareBasicAuthenticationFilter(), SecurityContextHolderFilter.class).addFilterAfter(
                        requestResponseFilter(), ExceptionTranslationFilter.class)
                .addFilterAfter(correlationHeaderFilter(), RequestResponseFilter.class).addFilterAfter(fineractInstanceModeApiFilter(),
                        CorrelationHeaderFilter.class)
                // View-only users: refuse every data-changing request once the user is authenticated and authorized
                .addFilterAfter(new org.apache.fineract.selfservice.security.filter.SelfServiceViewOnlyFilter(),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // === PUBLIC ENDPOINTS ===
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/user")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/client-user")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/client-user/confirm")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/registration")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/user"),
                                antMatcher(HttpMethod.POST, "/v1/self/registration/user"))
                        .permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/client-user"),
                                antMatcher(HttpMethod.POST, "/v1/self/registration/client-user"))
                        .permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/registration/client-user/confirm"),
                                antMatcher(HttpMethod.POST, "/v1/self/registration/client-user/confirm"))
                        .permitAll()

                        // Client Identity documents available in the platform
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/v1/self/registration/identifiers")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.GET, "/v1/self/registration/identifiers")).permitAll()

                        // External System Client Identity
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/identity/retrieve")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/identity/retrieve")).permitAll()

                        // Self authentication (login)
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/authentication")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/authentication")).permitAll()

                        // Password Reset
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/password")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/password")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/password/request")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/password/request")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/password/renew")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/password/renew")).permitAll()

                        // Public loan simulation endpoints (MX-250)
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/v1/self/loans/simulate/products")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.GET, "/v1/self/loans/simulate/products")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.GET, "/api/v1/self/loans/simulate/template")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.GET, "/v1/self/loans/simulate/template")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/api/v1/self/loans/simulate")).permitAll()
                        .requestMatchers(antMatcher(HttpMethod.POST, "/v1/self/loans/simulate")).permitAll()
                        // All other self-service endpoints require self-service authentication and must
                        // pass the self-service authorization manager (guards self vs non-self
                        // traffic).
                        .requestMatchers(antMatcher("/api/v1/self/**"), antMatcher("/v1/self/**"))
                        .access(SelfServiceUserAuthorizationManager.selfServiceUserAuthManager()).anyRequest().permitAll());

        // CORS if needed for mobile/web clients
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    public SelfServiceEnablerSecurityFilter selfServiceEnablerSecurityFilter() {
        return new SelfServiceEnablerSecurityFilter(configurationDomainService);
    }

    public RequestResponseFilter requestResponseFilter() {
        return new RequestResponseFilter();
    }

    public LoanCOBApiFilter loanCOBApiFilter() {
        return new LoanCOBApiFilter(loanCOBFilterHelper);
    }

    public TwoFactorAuthenticationFilter twoFactorAuthenticationFilter() {
        TwoFactorService twoFactorService = applicationContext.getBean(TwoFactorService.class);
        return new TwoFactorAuthenticationFilter(twoFactorService);
    }

    public FineractInstanceModeApiFilter fineractInstanceModeApiFilter() {
        return new FineractInstanceModeApiFilter(fineractProperties);
    }

    public IdempotencyStoreFilter idempotencyStoreFilter() {
        return new IdempotencyStoreFilter(fineractRequestContextHolder, idempotencyStoreHelper, fineractProperties);
    }

    public CorrelationHeaderFilter correlationHeaderFilter() {
        return new CorrelationHeaderFilter(fineractProperties, mdcWrapper);
    }

    public SelfServiceBasicAuthenticationFilter tenantAwareBasicAuthenticationFilter() throws Exception {
        SelfServiceBasicAuthenticationFilter filter = new SelfServiceBasicAuthenticationFilter(selfServiceAuthenticationManager(),
                selfServiceBasicAuthenticationEntryPoint(), toApiJsonSerializer, configurationDomainService, cacheWritePlatformService,
                userNotificationService, basicAuthTenantDetailsService, businessDateReadPlatformService);

        // Inject custom converter to handle both Password and Token auth
        filter.setAuthenticationConverter(new SelfServiceAuthenticationConverter());

        // Must match both /api/v1/self/** and /v1/self/** endpoints.
        // Some self-service resources (e.g. runreports) are registered under /v1/self/** without
        // the /api prefix, so authentication filter must cover both patterns.
        filter.setRequestMatcher(new org.springframework.security.web.util.matcher.OrRequestMatcher(
                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/v1/self/**"),
                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/v1/self/**")));
        return filter;
    }

    // Add new Bean for Token Provider:
    @Bean(name = "selfServiceTokenAuthenticationProvider")
    public SelfServiceTokenAuthenticationProvider selfServiceTokenAuthProvider() {
        return new SelfServiceTokenAuthenticationProvider(tokenService, userDetailsService,
                org.apache.fineract.selfservice.security.service.SelfServiceUserDetailsChecker.accountStatusChecker());
    }

    @Bean(name = "selfServiceBasicAuthenticationEntryPoint")
    public BasicAuthenticationEntryPoint selfServiceBasicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("Fineract Self Service API");
        return entryPoint;
    }

    @Bean(name = "selfServiceAuthenticationProvider")
    public DaoAuthenticationProvider selfServiceAuthProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(selfServicePasswordEncoder());
        authProvider.setPreAuthenticationChecks(
                org.apache.fineract.selfservice.security.service.SelfServiceUserDetailsChecker.accountStatusChecker());
        authProvider.setPostAuthenticationChecks(new org.springframework.security.authentication.AccountStatusUserDetailsChecker());
        return authProvider;
    }

    public PasswordEncoder selfServicePasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public AuthenticationManager selfServiceAuthenticationManager() throws Exception {
        ProviderManager providerManager = new ProviderManager(selfServiceAuthProvider(), selfServiceTokenAuthProvider());
        providerManager.setEraseCredentialsAfterAuthentication(false);
        return providerManager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(java.util.List.of("*"));
        config.setAllowedMethods(java.util.List.of("*"));
        config.setAllowCredentials(true);
        config.setAllowedHeaders(java.util.List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
