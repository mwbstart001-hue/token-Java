package com.example.tokenservice.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

@Component
public abstract class TokenStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(TokenStrategyFactory.class);

    @Lookup("simpleTokenGenerator")
    public abstract TokenGenerator getSimpleGenerator();

    @Lookup("simpleTokenValidator")
    public abstract TokenValidator getSimpleValidator();

    @Lookup("rs256TokenGenerator")
    public abstract TokenGenerator getRs256Generator();

    @Lookup("rs256TokenValidator")
    public abstract TokenValidator getRs256Validator();

    @Lookup("hs256TokenGenerator")
    public abstract TokenGenerator getHs256Generator();

    @Lookup("hs256TokenValidator")
    public abstract TokenValidator getHs256Validator();

    @Lookup("jwtTokenGenerator")
    public abstract TokenGenerator getJwtGenerator();

    @Lookup("jwtTokenValidator")
    public abstract TokenValidator getJwtValidator();

    public TokenGenerator getGenerator(String strategyType, String algorithm) {
        if (strategyType == null) {
            strategyType = "JWT";
        }
        if (algorithm == null) {
            algorithm = "RS256";
        }

        String upperType = strategyType.toUpperCase();
        String upperAlgo = algorithm.toUpperCase();

        if ("SIMPLE".equals(upperType)) {
            log.debug("通过 @Lookup 获取 SimpleTokenGenerator 原型实例");
            return getSimpleGenerator();
        } else if ("RS256".equals(upperAlgo)) {
            log.debug("通过 @Lookup 获取 RS256TokenGenerator 原型实例");
            return getRs256Generator();
        } else if ("HS256".equals(upperAlgo)) {
            log.debug("通过 @Lookup 获取 HS256TokenGenerator 原型实例");
            return getHs256Generator();
        } else {
            log.debug("通过 @Lookup 获取 JwtTokenGenerator 原型实例");
            return getJwtGenerator();
        }
    }

    public TokenValidator getValidator(String strategyType, String algorithm) {
        if (strategyType == null) {
            strategyType = "JWT";
        }
        if (algorithm == null) {
            algorithm = "RS256";
        }

        String upperType = strategyType.toUpperCase();
        String upperAlgo = algorithm.toUpperCase();

        if ("SIMPLE".equals(upperType)) {
            log.debug("通过 @Lookup 获取 SimpleTokenValidator 原型实例");
            return getSimpleValidator();
        } else if ("RS256".equals(upperAlgo)) {
            log.debug("通过 @Lookup 获取 RS256TokenValidator 原型实例");
            return getRs256Validator();
        } else if ("HS256".equals(upperAlgo)) {
            log.debug("通过 @Lookup 获取 HS256TokenValidator 原型实例");
            return getHs256Validator();
        } else {
            log.debug("通过 @Lookup 获取 JwtTokenValidator 原型实例");
            return getJwtValidator();
        }
    }

    public StrategyType getStrategyType(String strategyType, String algorithm) {
        if (strategyType == null) {
            strategyType = "JWT";
        }
        if (algorithm == null) {
            algorithm = "RS256";
        }

        String upperType = strategyType.toUpperCase();
        String upperAlgo = algorithm.toUpperCase();

        if ("SIMPLE".equals(upperType)) {
            return StrategyType.SIMPLE;
        } else if ("RS256".equals(upperAlgo)) {
            return StrategyType.RS256;
        } else if ("HS256".equals(upperAlgo)) {
            return StrategyType.HS256;
        } else {
            return StrategyType.JWT;
        }
    }

    public enum StrategyType {
        SIMPLE("simpleTokenGenerator", "simpleTokenValidator"),
        RS256("rs256TokenGenerator", "rs256TokenValidator"),
        HS256("hs256TokenGenerator", "hs256TokenValidator"),
        JWT("jwtTokenGenerator", "jwtTokenValidator");

        private final String generatorBeanName;
        private final String validatorBeanName;

        StrategyType(String generatorBeanName, String validatorBeanName) {
            this.generatorBeanName = generatorBeanName;
            this.validatorBeanName = validatorBeanName;
        }

        public String getGeneratorBeanName() {
            return generatorBeanName;
        }

        public String getValidatorBeanName() {
            return validatorBeanName;
        }
    }
}
