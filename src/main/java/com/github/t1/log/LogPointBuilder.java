package com.github.t1.log;

import static com.github.t1.log.LogLevel.*;
import static java.lang.Character.*;
import static java.util.Collections.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import javax.enterprise.inject.Instance;

import org.slf4j.*;

import com.github.t1.log.LogPoint.StandardLogPoint;
import com.github.t1.log.LogPoint.ThrowableLogPoint;
import com.github.t1.stereotypes.Annotations;

class LogPointBuilder {
    private static final int INVALID_PARAMETER_EXPRESSION = Integer.MIN_VALUE;
    private static final Pattern VAR = Pattern.compile("\\{(?<expression>[^}]*)\\}");
    private static final Pattern NUMERIC = Pattern.compile("(\\+|-|)\\d+");

    private final Method method;
    private final Instance<LogContextVariable> variables;
    private final Converters converters;

    private final Logged logged;
    private final List<LogParameter> logParameters;
    private final boolean logThrowable;

    private int defaultIndex = 0;

    public LogPointBuilder(Method method, Instance<LogContextVariable> variables, Converters converters) {
        this.method = method;
        this.variables = variables;
        this.converters = converters;

        this.logged = Annotations.on(method).getAnnotation(Logged.class);

        List<Parameter> rawParams = rawParams();
        this.logParameters = buildParams(rawParams);
        this.logThrowable = lastParamIsThrowable(rawParams);
    }

    private List<LogParameter> buildParams(List<Parameter> rawParams) {
        final List<LogParameter> result = new ArrayList<>();
        if (defaultLogMessage()) {
            for (Parameter parameter : rawParams) {
                if (!parameter.isAnnotationPresent(DontLog.class)) {
                    result.add(logParam(parameter));
                }
            }
        } else {
            Matcher matcher = VAR.matcher(logged.value());
            while (matcher.find()) {
                String expression = matcher.group("expression");
                int index = index(expression);
                if (index == INVALID_PARAMETER_EXPRESSION)
                    result.add(new StaticLogParameter("invalid log parameter expression: " + expression));
                else if (index < 0 || index >= rawParams.size())
                    result.add(new StaticLogParameter("invalid log parameter index: " + index));
                else
                    result.add(logParam(rawParams.get(index)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private List<Parameter> rawParams() {
        List<Parameter> list = new ArrayList<>();
        for (int index = 0; index < method.getParameterTypes().length; index++) {
            list.add(new Parameter(method, index));
        }
        return unmodifiableList(list);
    }

    private LogParameter logParam(Parameter parameter) {
        return new RealLogParameter(parameter, converters);
    }

    private boolean lastParamIsThrowable(List<Parameter> params) {
        if (params.isEmpty())
            return false;
        Parameter lastParam = params.get(params.size() - 1);
        return Throwable.class.isAssignableFrom(lastParam.type());
    }

    public LogPoint build() {
        Logger logger = LoggerFactory.getLogger(loggerType());
        LogLevel level = resolveLevel();
        String message = parseMessage();
        boolean logResult = method.getReturnType() != void.class;

        if (logThrowable) {
            int last = logParameters.size() - 1;
            List<LogParameter> parameters = logParameters.subList(0, last);
            LogParameter throwableParameter = logParameters.get(last);
            return new ThrowableLogPoint(logger, level, message, parameters, throwableParameter, variables, logResult,
                    converters);
        }
        return new StandardLogPoint(logger, level, message, logParameters, variables, logResult, converters);
    }

    private LogLevel resolveLevel() {
        return resolveLevel(method);
    }

    private LogLevel resolveLevel(AnnotatedElement element) {
        Logged logged = logged(element);
        if (logged != null) {
            LogLevel level = logged.level();
            if (level != _DERIVED_)
                return level;
        }
        Class<?> container = container(element);
        if (container != null)
            return resolveLevel(container);
        return DEBUG;
    }

    private Logged logged(AnnotatedElement element) {
        return annotations(element).getAnnotation(Logged.class);
    }

    private AnnotatedElement annotations(AnnotatedElement element) {
        if (element instanceof Method)
            return Annotations.on((Method) element);
        return Annotations.on((Class<?>) element);
    }

    private Class<?> container(AnnotatedElement element) {
        if (element instanceof Member)
            return ((Member) element).getDeclaringClass();
        return ((Class<?>) element).getEnclosingClass();
    }

    private Class<?> loggerType() {
        Class<?> loggerType = logged.logger();
        if (loggerType == void.class) {
            // the method is declared in the target type, while context.getTarget() is the CDI proxy
            loggerType = method.getDeclaringClass();
            while (loggerType.getEnclosingClass() != null) {
                loggerType = loggerType.getEnclosingClass();
            }
        }
        return loggerType;
    }

    private String parseMessage() {
        if (defaultLogMessage()) {
            return camelToSpaces(method.getName()) + messageParamPlaceholders();
        } else {
            return stripPlaceholderBodies(logged.value());
        }
    }

    private String camelToSpaces(String string) {
        StringBuilder out = new StringBuilder();
        for (Character c : string.toCharArray()) {
            if (isUpperCase(c)) {
                out.append(' ');
                out.append(toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String messageParamPlaceholders() {
        StringBuilder out = new StringBuilder();
        int n = logParameters.size();
        if (logThrowable)
            --n;
        for (int i = 0; i < n; i++) {
            out.append(" {}");
        }
        return out.toString();
    }

    private String stripPlaceholderBodies(String message) {
        StringBuffer out = new StringBuffer();
        Matcher matcher = VAR.matcher(message);
        while (matcher.find()) {
            matcher.appendReplacement(out, "{}");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private boolean defaultLogMessage() {
        return "".equals(logged.value());
    }

    private int index(String expression) {
        if (expression.isEmpty())
            return defaultIndex++;
        if (isNumeric(expression))
            return Integer.parseInt(expression);
        return INVALID_PARAMETER_EXPRESSION;
    }

    private boolean isNumeric(String expression) {
        return NUMERIC.matcher(expression).matches();
    }
}
