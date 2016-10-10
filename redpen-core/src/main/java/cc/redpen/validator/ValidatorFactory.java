/**
 * redpen: a text inspection tool
 * Copyright (c) 2014-2015 Recruit Technologies Co., Ltd. and contributors
 * (see CONTRIBUTORS.md)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cc.redpen.validator;

import cc.redpen.RedPenException;
import cc.redpen.config.Configuration;
import cc.redpen.config.ValidatorConfiguration;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.join;

/**
 * Factory class of validators.
 */
public class ValidatorFactory {
    private static final String validatorPackage = Validator.class.getPackage().getName();
    private static final List<String> VALIDATOR_PACKAGES = asList(validatorPackage, validatorPackage + ".sentence", validatorPackage + ".section");
    static final Map<String, Validator> validators = new LinkedHashMap<>();

    static void registerValidator(Class<? extends Validator> clazz) {
        validators.put(clazz.getSimpleName().replace("Validator", ""), createValidator(clazz));
    }

    static {
        Reflections reflections = new Reflections("cc.redpen.validator");
        // register Validator implementations under cc.redpen.validator package
        reflections.getSubTypesOf(Validator.class).stream()
                .filter(validator -> !Modifier.isAbstract(validator.getModifiers()))
                .forEach(validator -> {
                    try {
                        registerValidator(validator);
                    } catch (RuntimeException ignored) {
                        // the validator doesn't implement default constructor
                    }
                });
    }

    public static List<ValidatorConfiguration> getConfigurations(String lang) {
        return validators.entrySet().stream().filter(e -> {
            List<String> supportedLanguages = e.getValue().getSupportedLanguages();
            return supportedLanguages.isEmpty() || supportedLanguages.contains(lang);
        }).map(e -> new ValidatorConfiguration(e.getKey(), toStrings(e.getValue().getProperties()))).collect(toList());
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> toStrings(Map<String, Object> properties) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : properties.entrySet()) {
            if (e.getValue() instanceof Iterable)
                result.put(e.getKey(), join((Iterable)e.getValue(), ','));
            else
                result.put(e.getKey(), e.getValue().toString());
        }
        return result;
    }

    public static Validator getInstance(String validatorName) throws RedPenException {
        Configuration conf = Configuration.builder().addValidatorConfig(new ValidatorConfiguration(validatorName)).build();
        return getInstance(conf.getValidatorConfigs().get(0), conf);
    }

    public static Validator getInstance(ValidatorConfiguration config, Configuration globalConfig) throws RedPenException {
        Validator prototype = validators.get(config.getConfigurationName());
        Class<? extends Validator> validatorClass = prototype != null ? prototype.getClass() : loadPlugin(config.getConfigurationName());
        Validator validator = createValidator(validatorClass);
        validator.preInit(config, globalConfig);
        return validator;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Validator> loadPlugin(String name) throws RedPenException {
        for (String p : VALIDATOR_PACKAGES) {
            try {
                Class<? extends Validator> validatorClass = (Class)Class.forName(p + "." + name + "Validator");
                registerValidator(validatorClass);
                return validatorClass;
            }
            catch (ClassNotFoundException ignore) {
            }
        }
        throw new RedPenException("There is no such validator: " + name);
    }

    private static Validator createValidator(Class<? extends Validator> clazz) {
        try {
            return clazz.newInstance();
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Cannot create instance of " + clazz + " using default constructor");
        }
    }
}
