package dev.aerogel.gradle;

import dev.aerogel.api.mixin.At;
import dev.aerogel.api.mixin.AccessorDefinition;
import dev.aerogel.api.mixin.ConstantSelector;
import dev.aerogel.api.mixin.FieldTarget;
import dev.aerogel.api.mixin.InjectionDefinition;
import dev.aerogel.api.mixin.InjectorOptions;
import dev.aerogel.api.mixin.InvokerDefinition;
import dev.aerogel.api.mixin.JvmMemberTarget;
import dev.aerogel.api.mixin.MemberTarget;
import dev.aerogel.api.mixin.MethodTarget;
import dev.aerogel.api.mixin.MixinDefinition;
import dev.aerogel.api.mixin.MixinMemberDefinition;
import dev.aerogel.api.mixin.MixinOperationDefinition;
import dev.aerogel.api.mixin.MixinSlice;
import dev.aerogel.api.mixin.ModifyArgDefinition;
import dev.aerogel.api.mixin.ModifyArgsDefinition;
import dev.aerogel.api.mixin.ModifyConstantDefinition;
import dev.aerogel.api.mixin.ModifyVariableDefinition;
import dev.aerogel.api.mixin.OverwriteDefinition;
import dev.aerogel.api.mixin.RedirectDefinition;
import dev.aerogel.api.mixin.RedirectFieldAccess;
import dev.aerogel.api.mixin.ShadowFieldDefinition;
import dev.aerogel.api.mixin.ShadowMethodDefinition;
import dev.aerogel.api.mixin.TypeTarget;
import dev.aerogel.api.mixin.UniqueFieldDefinition;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.CallableReference;
import kotlin.jvm.internal.FunctionBase;
import kotlin.reflect.KClass;
import kotlin.reflect.KDeclarationContainer;
import kotlin.reflect.KFunction;
import kotlin.reflect.KProperty;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Lowers compiled Kotlin definitions to ordinary Sponge Mixin classes. */
public abstract class GenerateAerogelMixinBytecode extends DefaultTask {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String FINAL = "Lorg/spongepowered/asm/mixin/Final;";
    private static final String MUTABLE = "Lorg/spongepowered/asm/mixin/Mutable;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SLICE = "Lorg/spongepowered/asm/mixin/injection/Slice;";
    private static final String CONSTANT = "Lorg/spongepowered/asm/mixin/injection/Constant;";
    private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
    private static final String CALLBACK_INFO =
        "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_RETURNABLE =
        "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String ARGS = "org/spongepowered/asm/mixin/injection/invoke/arg/Args";
    private static final String DEFINITION = "dev/aerogel/api/mixin/MixinDefinition";
    private static final String OPERATION = "dev/aerogel/api/mixin/MixinOperationDefinition";

    @InputFile public abstract RegularFileProperty getIndexFile();
    @Classpath public abstract ConfigurableFileCollection getCompilationClasspath();
    @OutputDirectory public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        try {
            clean(output);
            Files.createDirectories(output);
            List<URL> urls = getCompilationClasspath().getFiles().stream().map(file -> {
                try {
                    return file.toURI().toURL();
                } catch (IOException exception) {
                    throw new GradleException("Invalid Mixin compiler classpath entry: " + file, exception);
                }
            }).toList();
            try (URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader())) {
                for (String line : Files.readAllLines(
                    getIndexFile().get().getAsFile().toPath(), StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) generateOne(line, loader, output);
                }
            }
        } catch (IOException | ReflectiveOperationException exception) {
            throw new GradleException("Cannot compile Aerogel Kotlin Mixins", exception);
        }
    }

    private static void generateOne(String indexLine, ClassLoader loader, Path output)
        throws ReflectiveOperationException, IOException {
        String[] entry = indexLine.split("\\t", -1);
        if (entry.length != 3) throw new GradleException("Malformed Aerogel Mixin index: " + indexLine);
        String mixinName = entry[0];
        String definitionClassName = entry[1];
        String mixinPackage = entry[2];
        Class<?> definitionClass = Class.forName(definitionClassName, true, loader);
        Field definitionField = definitionClass.getField("definition");
        Object value = definitionField.get(null);
        if (!(value instanceof MixinDefinition<?> definition)) {
            throw new GradleException(definitionClassName + ".definition is not a MixinDefinition");
        }

        String mixinInternalName = mixinPackage.replace('.', '/') + "/" + mixinName;
        String definitionInternalName = definitionClassName.replace('.', '/');
        Path classFile = output.resolve(mixinInternalName + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, generateClass(mixinInternalName, definitionInternalName, definition));
    }

    private static byte[] generateClass(
        String mixinInternalName,
        String definitionInternalName,
        MixinDefinition<?> definition
    ) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            mixinInternalName, null, "java/lang/Object", null);
        AnnotationVisitor mixin = writer.visitAnnotation(MIXIN, false);
        AnnotationVisitor targets = mixin.visitArray("value");
        targets.visit(null, Type.getType(definition.getTarget()));
        targets.visitEnd();
        mixin.visit("priority", definition.getPriority());
        mixin.visitEnd();
        generateConstructor(writer);

        for (MixinMemberDefinition member : definition.getMembers()) {
            if (member instanceof AccessorDefinition accessor) {
                generateAccessor(writer, accessor);
            } else if (member instanceof InvokerDefinition invoker) {
                generateInvoker(writer, invoker);
            } else if (member instanceof UniqueFieldDefinition uniqueField) {
                generateUniqueField(writer, mixinInternalName, uniqueField);
            } else if (member instanceof ShadowFieldDefinition shadowField) {
                generateShadowField(writer, mixinInternalName, shadowField);
            } else if (member instanceof ShadowMethodDefinition shadowMethod) {
                generateShadowMethod(writer, mixinInternalName, shadowMethod);
            } else {
                throw new GradleException("Unsupported Aerogel Mixin member: " + member.getClass().getName());
            }
        }

        List<MixinOperationDefinition> operations = definition.getOperations();
        for (int index = 0; index < operations.size(); index++) {
            MixinOperationDefinition operation = operations.get(index);
            if (operation instanceof InjectionDefinition injection) {
                generateInject(writer, definitionInternalName, definition.getTarget(), injection, index);
            } else if (operation instanceof ModifyArgDefinition modifyArg) {
                generateModifyArg(writer, definitionInternalName, definition.getTarget(), modifyArg, index);
            } else if (operation instanceof ModifyArgsDefinition modifyArgs) {
                generateModifyArgs(writer, definitionInternalName, definition.getTarget(), modifyArgs, index);
            } else if (operation instanceof ModifyVariableDefinition modifyVariable) {
                generateModifyVariable(writer, definitionInternalName, definition.getTarget(), modifyVariable, index);
            } else if (operation instanceof ModifyConstantDefinition modifyConstant) {
                generateModifyConstant(writer, definitionInternalName, definition.getTarget(), modifyConstant, index);
            } else if (operation instanceof RedirectDefinition redirect) {
                generateRedirect(writer, definitionInternalName, definition.getTarget(), redirect, index);
            } else if (operation instanceof OverwriteDefinition overwrite) {
                generateOverwrite(writer, definitionInternalName, definition.getTarget(), overwrite, index);
            } else {
                throw new GradleException("Unsupported Aerogel Mixin operation: " + operation.getClass().getName());
            }
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void generateConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PROTECTED, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private static void generateAccessor(ClassWriter writer, AccessorDefinition definition) {
        FieldSelector selected = fieldSelector(definition.getField());
        if (selected.isStatic()) {
            throw new GradleException("Use an instance property reference for accessor: "
                + selected.owner().getName() + "::" + selected.name());
        }
        generateAccessorMethod(writer, definition.getGetterName(),
            Type.getMethodDescriptor(Type.getType(selected.descriptor())), selected.name(), definition.getRemap());
        if (definition.getSetterName() != null) {
            generateAccessorMethod(writer, definition.getSetterName(),
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(selected.descriptor())),
                selected.name(), definition.getRemap());
        }
    }

    private static void generateAccessorMethod(
        ClassWriter writer, String bridgeName, String descriptor, String targetName, boolean remap
    ) {
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, bridgeName, descriptor, null, null);
        AnnotationVisitor annotation = method.visitAnnotation(ACCESSOR, true);
        annotation.visit("value", targetName);
        annotation.visit("remap", remap);
        annotation.visitEnd();
        method.visitEnd();
    }

    private static void generateInvoker(ClassWriter writer, InvokerDefinition definition) {
        CallableSelector selected = selector(definition.getMethod());
        rejectStaticTarget(selected, "invoker");
        if (selected.constructor()) {
            throw new GradleException("Constructor invokers require a static factory and are not instance invokers");
        }
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            definition.getBridgeName(), selected.descriptor(), null, null);
        AnnotationVisitor annotation = method.visitAnnotation(INVOKER, true);
        annotation.visit("value", selected.name());
        annotation.visit("remap", definition.getRemap());
        annotation.visitEnd();
        method.visitEnd();
    }

    private static void generateUniqueField(
        ClassWriter writer, String mixinInternalName, UniqueFieldDefinition definition
    ) {
        String fieldDescriptor = Type.getDescriptor(definition.getFieldType());
        var field = writer.visitField(Opcodes.ACC_PRIVATE, definition.getFieldName(),
            fieldDescriptor, null, null);
        AnnotationVisitor fieldAnnotation = field.visitAnnotation(UNIQUE, true);
        fieldAnnotation.visit("silent", definition.getSilent());
        fieldAnnotation.visitEnd();
        field.visitEnd();

        MethodVisitor getter = writer.visitMethod(Opcodes.ACC_PUBLIC, definition.getGetterName(),
            Type.getMethodDescriptor(Type.getType(fieldDescriptor)), null, null);
        emitUnique(getter, true);
        getter.visitCode();
        getter.visitVarInsn(Opcodes.ALOAD, 0);
        getter.visitFieldInsn(Opcodes.GETFIELD, mixinInternalName, definition.getFieldName(), fieldDescriptor);
        getter.visitInsn(Type.getType(fieldDescriptor).getOpcode(Opcodes.IRETURN));
        getter.visitMaxs(0, 0);
        getter.visitEnd();

        MethodVisitor setter = writer.visitMethod(Opcodes.ACC_PUBLIC, definition.getSetterName(),
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(fieldDescriptor)), null, null);
        emitUnique(setter, true);
        setter.visitCode();
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        Type fieldType = Type.getType(fieldDescriptor);
        setter.visitVarInsn(fieldType.getOpcode(Opcodes.ILOAD), 1);
        setter.visitFieldInsn(Opcodes.PUTFIELD, mixinInternalName, definition.getFieldName(), fieldDescriptor);
        setter.visitInsn(Opcodes.RETURN);
        setter.visitMaxs(0, 0);
        setter.visitEnd();
    }

    private static void emitUnique(MethodVisitor method, boolean silent) {
        AnnotationVisitor annotation = method.visitAnnotation(UNIQUE, true);
        annotation.visit("silent", silent);
        annotation.visitEnd();
    }

    private static void generateShadowField(
        ClassWriter writer, String mixinInternalName, ShadowFieldDefinition definition
    ) {
        FieldSelector selected = fieldSelector(definition.getField());
        if (selected.isStatic()) {
            throw new GradleException("Static shadow fields require a static property selector");
        }
        BytecodeField target = bytecodeField(selected.owner(), selected.name());
        int access = target.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE
            | Opcodes.ACC_FINAL | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT);
        var field = writer.visitField(access, selected.name(), selected.descriptor(), null, null);
        emitShadow(field.visitAnnotation(SHADOW, true), definition.getAliases(), definition.getRemap());
        if ((access & Opcodes.ACC_FINAL) != 0) {
            AnnotationVisitor finalAnnotation = field.visitAnnotation(FINAL, true);
            finalAnnotation.visitEnd();
        }
        if (definition.getMutableFinal()) {
            if ((access & Opcodes.ACC_FINAL) == 0) {
                throw new GradleException("mutableFinalShadow requires a final target field: "
                    + selected.owner().getName() + "::" + selected.name());
            }
            AnnotationVisitor mutableAnnotation = field.visitAnnotation(MUTABLE, true);
            mutableAnnotation.visitEnd();
        }
        field.visitEnd();
        generateFieldBridgeMethods(writer, mixinInternalName, selected.name(), selected.descriptor(),
            definition.getGetterName(), definition.getSetterName());
    }

    private static void generateShadowMethod(
        ClassWriter writer, String mixinInternalName, ShadowMethodDefinition definition
    ) {
        CallableSelector selected = selector(definition.getMethod());
        rejectStaticTarget(selected, "shadow");
        BytecodeMethod target = bytecodeMethod(selected.owner(), selected.name(), selected.descriptor());
        if (target == null) throw new GradleException("Cannot resolve shadow target " + selected);
        int access = target.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE
            | Opcodes.ACC_SYNCHRONIZED);
        MethodVisitor shadow = writer.visitMethod(access, selected.name(), selected.descriptor(), null, null);
        emitShadow(shadow.visitAnnotation(SHADOW, true), definition.getAliases(), definition.getRemap());
        shadow.visitCode();
        shadow.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
        shadow.visitInsn(Opcodes.DUP);
        shadow.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
        shadow.visitInsn(Opcodes.ATHROW);
        shadow.visitMaxs(0, 0);
        shadow.visitEnd();

        MethodVisitor bridge = writer.visitMethod(Opcodes.ACC_PUBLIC, definition.getBridgeName(),
            selected.descriptor(), null, null);
        emitUnique(bridge, true);
        bridge.visitCode();
        bridge.visitVarInsn(Opcodes.ALOAD, 0);
        Type[] arguments = Type.getArgumentTypes(selected.descriptor());
        int local = 1;
        for (Type argument : arguments) {
            bridge.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), local);
            local += argument.getSize();
        }
        int opcode = (target.access() & Opcodes.ACC_PRIVATE) != 0
            ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
        bridge.visitMethodInsn(opcode, mixinInternalName, selected.name(), selected.descriptor(), false);
        bridge.visitInsn(Type.getReturnType(selected.descriptor()).getOpcode(Opcodes.IRETURN));
        bridge.visitMaxs(0, 0);
        bridge.visitEnd();
    }

    private static void emitShadow(
        AnnotationVisitor annotation, List<String> aliases, boolean remap
    ) {
        annotation.visit("remap", remap);
        if (!aliases.isEmpty()) {
            AnnotationVisitor values = annotation.visitArray("aliases");
            for (String alias : aliases) values.visit(null, alias);
            values.visitEnd();
        }
        annotation.visitEnd();
    }

    private static void generateFieldBridgeMethods(
        ClassWriter writer, String owner, String fieldName, String descriptor,
        String getterName, String setterName
    ) {
        Type type = Type.getType(descriptor);
        MethodVisitor getter = writer.visitMethod(Opcodes.ACC_PUBLIC, getterName,
            Type.getMethodDescriptor(type), null, null);
        emitUnique(getter, true);
        getter.visitCode();
        getter.visitVarInsn(Opcodes.ALOAD, 0);
        getter.visitFieldInsn(Opcodes.GETFIELD, owner, fieldName, descriptor);
        getter.visitInsn(type.getOpcode(Opcodes.IRETURN));
        getter.visitMaxs(0, 0);
        getter.visitEnd();
        if (setterName == null) return;
        MethodVisitor setter = writer.visitMethod(Opcodes.ACC_PUBLIC, setterName,
            Type.getMethodDescriptor(Type.VOID_TYPE, type), null, null);
        emitUnique(setter, true);
        setter.visitCode();
        setter.visitVarInsn(Opcodes.ALOAD, 0);
        setter.visitVarInsn(type.getOpcode(Opcodes.ILOAD), 1);
        setter.visitFieldInsn(Opcodes.PUTFIELD, owner, fieldName, descriptor);
        setter.visitInsn(Opcodes.RETURN);
        setter.visitMaxs(0, 0);
        setter.visitEnd();
    }

    private static void generateInject(
        ClassWriter writer, String definitionName, Class<?> target,
        InjectionDefinition injection, int index
    ) {
        CallableSelector selected = injection.getClassInitializer()
            ? new CallableSelector(target, "<clinit>", "()V", true, false)
            : selector(injection.getMethod());
        if (selected.isStatic() != injection.getStaticHandler()) {
            throw new GradleException("Use injectStatic for static methods and inject for instance methods: "
                + selected.owner().getName() + "::" + selected.name());
        }
        Type targetType = Type.getMethodType(selected.descriptor());
        Type callback = targetType.getReturnType().equals(Type.VOID_TYPE)
            ? Type.getObjectType(CALLBACK_INFO) : Type.getObjectType(CALLBACK_RETURNABLE);
        Type[] handlerArguments = append(targetType.getArgumentTypes(), callback);
        for (Class<?> localType : injection.getLocalTypes()) {
            handlerArguments = append(handlerArguments, Type.getType(localType));
        }
        String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, handlerArguments);
        MethodVisitor method = privateMethod(writer, "aerogel$inject$" + index, descriptor,
            injection.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(INJECT, true);
        emitMethods(annotation, selected);
        if (!injection.getId().isEmpty()) annotation.visit("id", injection.getId());
        emitSlices(annotation, injection.getSlices(), true);
        AnnotationVisitor points = annotation.visitArray("at");
        for (At point : injection.getPoints()) emitAt(points.visitAnnotation(null, AT), point);
        points.visitEnd();
        annotation.visit("cancellable", injection.getCancellable());
        annotation.visitEnum("locals", "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;",
            switch (injection.getLocals()) {
                case NONE -> "NO_CAPTURE";
                default -> injection.getLocals().name();
            });
        emitOptions(annotation, injection.getOptions());
        annotation.visitEnd();
        emitGroup(method, injection.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !injection.getStaticHandler());
    }

    private static void generateModifyArg(
        ClassWriter writer, String definitionName, Class<?> target,
        ModifyArgDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "modifyArg");
        Type value = Type.getType(operation.getValueType());
        String descriptor = Type.getMethodDescriptor(value, value);
        MethodVisitor method = privateMethod(writer, "aerogel$modifyArg$" + index, descriptor,
            operation.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(MODIFY_ARG, true);
        emitMethods(annotation, selected);
        emitSlice(annotation, operation.getSlice());
        AnnotationVisitor point = annotation.visitAnnotation("at", AT);
        emitAt(point, operation.getAt());
        annotation.visit("index", operation.getIndex());
        emitOptions(annotation, operation.getOptions());
        annotation.visitEnd();
        emitGroup(method, operation.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !operation.getStaticHandler());
    }

    private static void generateModifyArgs(
        ClassWriter writer, String definitionName, Class<?> target,
        ModifyArgsDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "modifyArgs");
        String descriptor = "(L" + ARGS + ";)V";
        MethodVisitor method = privateMethod(writer, "aerogel$modifyArgs$" + index, descriptor,
            operation.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(MODIFY_ARGS, true);
        emitMethods(annotation, selected);
        emitSlice(annotation, operation.getSlice());
        emitAt(annotation.visitAnnotation("at", AT), operation.getAt());
        emitOptions(annotation, operation.getOptions());
        annotation.visitEnd();
        emitGroup(method, operation.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !operation.getStaticHandler());
    }

    private static void generateModifyVariable(
        ClassWriter writer, String definitionName, Class<?> target,
        ModifyVariableDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "modifyVariable");
        Type value = Type.getType(operation.getValueType());
        String descriptor = Type.getMethodDescriptor(value, value);
        MethodVisitor method = privateMethod(writer, "aerogel$modifyVariable$" + index, descriptor,
            operation.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(MODIFY_VARIABLE, true);
        emitMethods(annotation, selected);
        emitSlice(annotation, operation.getSlice());
        emitAt(annotation.visitAnnotation("at", AT), operation.getAt());
        annotation.visit("print", operation.getPrint());
        annotation.visit("ordinal", operation.getOrdinal());
        annotation.visit("index", operation.getIndex());
        AnnotationVisitor names = annotation.visitArray("name");
        for (String name : operation.getNames()) names.visit(null, name);
        names.visitEnd();
        annotation.visit("argsOnly", operation.getArgsOnly());
        emitOptions(annotation, operation.getOptions());
        annotation.visitEnd();
        emitGroup(method, operation.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !operation.getStaticHandler());
    }

    private static void generateModifyConstant(
        ClassWriter writer, String definitionName, Class<?> target,
        ModifyConstantDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "modifyConstant");
        Type value = Type.getType(operation.getValueType());
        String descriptor = Type.getMethodDescriptor(value, value);
        MethodVisitor method = privateMethod(writer, "aerogel$modifyConstant$" + index, descriptor,
            operation.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(MODIFY_CONSTANT, true);
        emitMethods(annotation, selected);
        emitSlices(annotation, operation.getSlices(), true);
        AnnotationVisitor constants = annotation.visitArray("constant");
        for (ConstantSelector selector : operation.getConstants()) {
            emitConstant(constants.visitAnnotation(null, CONSTANT), selector);
        }
        constants.visitEnd();
        emitOptions(annotation, operation.getOptions());
        annotation.visitEnd();
        emitGroup(method, operation.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !operation.getStaticHandler());
    }

    private static void generateRedirect(
        ClassWriter writer, String definitionName, Class<?> target,
        RedirectDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "redirect");
        String descriptor;
        int fieldOpcode = -1;
        if (operation.getTarget() instanceof MethodTarget methodTarget) {
            CallableSelector redirected = selector(methodTarget.getMethod());
            Type redirectedType = Type.getMethodType(redirected.descriptor());
            Type returnType = redirected.constructor()
                ? Type.getType(redirected.owner()) : redirectedType.getReturnType();
            Type[] arguments = redirectedType.getArgumentTypes();
            if (!redirected.isStatic() && !redirected.constructor()) {
                arguments = prepend(arguments, Type.getType(redirected.owner()));
            }
            descriptor = Type.getMethodDescriptor(returnType, arguments);
        } else if (operation.getTarget() instanceof FieldTarget fieldTarget) {
            FieldSelector field = fieldSelector(fieldTarget.getField());
            Type ownerType = Type.getType(field.owner());
            Type valueType = Type.getType(field.descriptor());
            if (operation.getFieldAccess() == RedirectFieldAccess.GET) {
                descriptor = field.isStatic()
                    ? Type.getMethodDescriptor(valueType)
                    : Type.getMethodDescriptor(valueType, ownerType);
                fieldOpcode = field.isStatic() ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
            } else if (operation.getFieldAccess() == RedirectFieldAccess.SET) {
                descriptor = field.isStatic()
                    ? Type.getMethodDescriptor(Type.VOID_TYPE, valueType)
                    : Type.getMethodDescriptor(Type.VOID_TYPE, ownerType, valueType);
                fieldOpcode = field.isStatic() ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
            } else {
                throw new GradleException("Field redirect must declare GET or SET access");
            }
        } else {
            throw new GradleException("Unsupported redirect target: " + operation.getTarget().getClass().getName());
        }
        MethodVisitor method = privateMethod(writer, "aerogel$redirect$" + index, descriptor,
            operation.getStaticHandler());
        AnnotationVisitor annotation = method.visitAnnotation(REDIRECT, true);
        emitMethods(annotation, selected);
        emitSlice(annotation, operation.getSlice());
        emitAt(annotation.visitAnnotation("at", AT), operation.getAt(), fieldOpcode);
        emitOptions(annotation, operation.getOptions());
        annotation.visitEnd();
        emitGroup(method, operation.getOptions());
        emitDelegate(method, definitionName, target, index, descriptor, !operation.getStaticHandler());
    }

    private static void generateOverwrite(
        ClassWriter writer, String definitionName, Class<?> target,
        OverwriteDefinition operation, int index
    ) {
        CallableSelector selected = selector(operation.getMethod());
        validateHandlerStatic(selected, operation.getStaticHandler(), "overwrite");
        int access = targetMethodAccess(target, selected);
        MethodVisitor method = writer.visitMethod(access, selected.name(), selected.descriptor(), null, null);
        AnnotationVisitor annotation = method.visitAnnotation(OVERWRITE, true);
        annotation.visitEnd();
        emitDelegate(method, definitionName, target, index, selected.descriptor(), !operation.getStaticHandler());
    }

    private static MethodVisitor privateMethod(ClassWriter writer, String name, String descriptor) {
        return privateMethod(writer, name, descriptor, false);
    }

    private static MethodVisitor privateMethod(
        ClassWriter writer, String name, String descriptor, boolean isStatic
    ) {
        return writer.visitMethod(Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0),
            name, descriptor, null, null);
    }

    private static void emitMethods(AnnotationVisitor annotation, CallableSelector selected) {
        AnnotationVisitor methods = annotation.visitArray("method");
        methods.visit(null, selected.name() + selected.descriptor());
        methods.visitEnd();
    }

    private static void emitOptions(AnnotationVisitor annotation, InjectorOptions options) {
        annotation.visit("remap", options.getRemap());
        annotation.visit("require", options.getRequire());
        annotation.visit("expect", options.getExpect());
        annotation.visit("allow", options.getAllow());
        if (!options.getConstraints().isEmpty()) annotation.visit("constraints", options.getConstraints());
        annotation.visit("order", options.getOrder());
    }

    private static void emitGroup(MethodVisitor method, InjectorOptions options) {
        if (options.getGroup() == null) return;
        AnnotationVisitor annotation = method.visitAnnotation(GROUP, false);
        annotation.visit("name", options.getGroup().getName());
        annotation.visit("min", options.getGroup().getMin());
        annotation.visit("max", options.getGroup().getMax());
        annotation.visitEnd();
    }

    private static void emitSlice(AnnotationVisitor annotation, MixinSlice slice) {
        if (slice == null) return;
        emitSliceValue(annotation.visitAnnotation("slice", SLICE), slice);
    }

    private static void emitSlices(AnnotationVisitor annotation, List<MixinSlice> slices, boolean array) {
        if (slices.isEmpty()) return;
        AnnotationVisitor values = annotation.visitArray("slice");
        for (MixinSlice slice : slices) emitSliceValue(values.visitAnnotation(null, SLICE), slice);
        values.visitEnd();
    }

    private static void emitSliceValue(AnnotationVisitor annotation, MixinSlice slice) {
        if (!slice.getId().isEmpty()) annotation.visit("id", slice.getId());
        emitAt(annotation.visitAnnotation("from", AT), slice.getFrom());
        emitAt(annotation.visitAnnotation("to", AT), slice.getTo());
        annotation.visitEnd();
    }

    private static void emitAt(AnnotationVisitor annotation, At at) {
        emitAt(annotation, at, -1);
    }

    private static void emitAt(AnnotationVisitor annotation, At at, int defaultOpcode) {
        if (!at.getId().isEmpty()) annotation.visit("id", at.getId());
        annotation.visit("value", at.getValue());
        if (!at.getSlice().isEmpty()) annotation.visit("slice", at.getSlice());
        annotation.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", at.getShift().name());
        annotation.visit("by", at.getBy());
        if (!at.getArgs().isEmpty()) {
            AnnotationVisitor args = annotation.visitArray("args");
            for (String argument : at.getArgs()) args.visit(null, argument);
            args.visitEnd();
        }
        if (at.getTarget() != null) annotation.visit("target", renderTarget(at.getTarget()));
        annotation.visit("ordinal", at.getOrdinal());
        annotation.visit("opcode", at.getOpcode() >= 0 ? at.getOpcode() : defaultOpcode);
        annotation.visit("remap", at.getRemap());
        annotation.visit("unsafe", at.getUnsafe());
        annotation.visitEnd();
    }

    private static void emitConstant(AnnotationVisitor annotation, ConstantSelector selector) {
        switch (selector.getKind()) {
            case ANY -> { }
            case NULL -> annotation.visit("nullValue", true);
            case INT -> annotation.visit("intValue", selector.getValue());
            case FLOAT -> annotation.visit("floatValue", selector.getValue());
            case LONG -> annotation.visit("longValue", selector.getValue());
            case DOUBLE -> annotation.visit("doubleValue", selector.getValue());
            case STRING -> annotation.visit("stringValue", selector.getValue());
            case CLASS -> annotation.visit("classValue", Type.getType((Class<?>) selector.getValue()));
        }
        annotation.visit("ordinal", selector.getOrdinal());
        if (!selector.getSlice().isEmpty()) annotation.visit("slice", selector.getSlice());
        if (!selector.getConditions().isEmpty()) {
            AnnotationVisitor conditions = annotation.visitArray("expandZeroConditions");
            for (ConstantSelector.ZeroCondition condition : selector.getConditions()) {
                conditions.visitEnum(null, "Lorg/spongepowered/asm/mixin/injection/Constant$Condition;", condition.name());
            }
            conditions.visitEnd();
        }
        annotation.visit("log", selector.getLog());
        annotation.visitEnd();
    }

    private static String renderTarget(MemberTarget target) {
        if (target instanceof MethodTarget method) {
            CallableSelector selected = selector(method.getMethod());
            if (selected.constructor()) {
                Type constructor = Type.getMethodType(selected.descriptor());
                return Type.getMethodDescriptor(
                    Type.getType(selected.owner()), constructor.getArgumentTypes());
            }
            return "L" + Type.getInternalName(selected.owner()) + ";" + selected.name() + selected.descriptor();
        }
        if (target instanceof FieldTarget property) {
            FieldSelector selected = fieldSelector(property.getField());
            return "L" + Type.getInternalName(selected.owner()) + ";" + selected.name()
                + ":" + selected.descriptor();
        }
        if (target instanceof TypeTarget type) {
            return "L" + Type.getInternalName(type.getType()) + ";";
        }
        if (target instanceof JvmMemberTarget explicit) return explicit.getSelector();
        throw new GradleException("Unsupported Mixin member target: " + target.getClass().getName());
    }

    private static void emitDelegate(
        MethodVisitor method, String definitionName, Class<?> target, int index,
        String handlerDescriptor, boolean receiver
    ) {
        Type methodType = Type.getMethodType(handlerDescriptor);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, definitionName, "definition", "L" + DEFINITION + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DEFINITION, "getOperations", "()Ljava/util/List;", false);
        pushInteger(method, index);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        method.visitTypeInsn(Opcodes.CHECKCAST, OPERATION);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, OPERATION, "getHandler", "()Ljava/lang/Object;", true);

        Type[] arguments = methodType.getArgumentTypes();
        int arity = arguments.length + (receiver ? 1 : 0);
        if (arity > 22) {
            throw new GradleException("Mixin handler has " + arity + " Kotlin arguments; maximum is 22");
        }
        String function = "kotlin/jvm/functions/Function" + arity;
        method.visitTypeInsn(Opcodes.CHECKCAST, function);
        if (receiver) {
            method.visitVarInsn(Opcodes.ALOAD, 0);
            method.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(target));
        }
        int local = receiver ? 1 : 0;
        for (Type argument : arguments) {
            method.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), local);
            box(method, argument);
            local += argument.getSize();
        }
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, function, "invoke",
            functionInvokeDescriptor(arity), true);
        emitReturn(method, methodType.getReturnType());
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void emitReturn(MethodVisitor method, Type type) {
        if (type.equals(Type.VOID_TYPE)) {
            method.visitInsn(Opcodes.POP);
            method.visitInsn(Opcodes.RETURN);
            return;
        }
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            method.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
            method.visitInsn(Opcodes.ARETURN);
            return;
        }
        String owner;
        String name;
        String descriptor;
        switch (type.getSort()) {
            case Type.BOOLEAN -> { owner = "java/lang/Boolean"; name = "booleanValue"; descriptor = "()Z"; }
            case Type.BYTE -> { owner = "java/lang/Byte"; name = "byteValue"; descriptor = "()B"; }
            case Type.CHAR -> { owner = "java/lang/Character"; name = "charValue"; descriptor = "()C"; }
            case Type.SHORT -> { owner = "java/lang/Short"; name = "shortValue"; descriptor = "()S"; }
            case Type.INT -> { owner = "java/lang/Number"; name = "intValue"; descriptor = "()I"; }
            case Type.FLOAT -> { owner = "java/lang/Number"; name = "floatValue"; descriptor = "()F"; }
            case Type.LONG -> { owner = "java/lang/Number"; name = "longValue"; descriptor = "()J"; }
            case Type.DOUBLE -> { owner = "java/lang/Number"; name = "doubleValue"; descriptor = "()D"; }
            default -> throw new GradleException("Unsupported primitive Mixin return: " + type);
        }
        method.visitTypeInsn(Opcodes.CHECKCAST, owner);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false);
        method.visitInsn(type.getOpcode(Opcodes.IRETURN));
    }

    private static CallableSelector selector(KFunction<?> function) {
        if (!(function instanceof CallableReference reference)) {
            throw new GradleException("Mixin selector must be a direct JVM method reference: " + function.getName());
        }
        String signature = reference.getSignature();
        int descriptorIndex = signature.indexOf('(');
        if (descriptorIndex <= 0) {
            throw new GradleException("Unsupported Kotlin method reference signature: " + signature);
        }
        String name = signature.substring(0, descriptorIndex);
        String descriptor = signature.substring(descriptorIndex);
        try {
            Type.getMethodType(descriptor);
        } catch (IllegalArgumentException exception) {
            throw new GradleException("Invalid JVM method reference signature: " + signature, exception);
        }
        Class<?> owner = ownerClass(reference);
        boolean constructor = name.equals("<init>");
        int referencedArity = function instanceof FunctionBase<?> functionBase
            ? functionBase.getArity() : Type.getArgumentTypes(descriptor).length + 1;
        boolean isStatic = !constructor && referencedArity == Type.getArgumentTypes(descriptor).length;
        return new CallableSelector(owner, name, descriptor, isStatic, constructor);
    }

    private static FieldSelector fieldSelector(KProperty<?> property) {
        if (!(property instanceof CallableReference reference)) {
            throw new GradleException("Mixin field selector must be a direct Kotlin property reference: " + property.getName());
        }
        Class<?> owner = ownerClass(reference);
        BytecodeField field = bytecodeField(owner, reference.getName());
        if (field != null) return new FieldSelector(owner, reference.getName(), field.descriptor(),
            (field.access() & Opcodes.ACC_STATIC) != 0);
        throw new GradleException("Cannot resolve field reference " + owner.getName() + "::" + reference.getName());
    }

    private static Class<?> ownerClass(CallableReference reference) {
        KDeclarationContainer owner = reference.getOwner();
        if (!(owner instanceof KClass<?> type)) {
            throw new GradleException("Mixin member reference has no JVM class owner: " + reference);
        }
        return JvmClassMappingKt.getJavaClass(type);
    }

    private static int targetMethodAccess(Class<?> owner, CallableSelector selector) {
        BytecodeMethod method = bytecodeMethod(owner, selector.name(), selector.descriptor());
        if (method == null) throw new GradleException("Cannot resolve overwrite target " + selector);
        return method.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE
            | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED);
    }

    private static BytecodeMethod bytecodeMethod(Class<?> owner, String name, String descriptor) {
        final BytecodeMethod[] found = new BytecodeMethod[1];
        readClass(owner, new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String candidate, String candidateDescriptor,
                                             String signature, String[] exceptions) {
                if (candidate.equals(name) && candidateDescriptor.equals(descriptor)) {
                    found[0] = new BytecodeMethod(access);
                }
                return null;
            }
        });
        return found[0];
    }

    private static BytecodeField bytecodeField(Class<?> owner, String name) {
        final BytecodeField[] found = new BytecodeField[1];
        readClass(owner, new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String candidate, String descriptor,
                                                               String signature, Object value) {
                if (candidate.equals(name)) found[0] = new BytecodeField(access, descriptor);
                return null;
            }
        });
        return found[0];
    }

    private static void readClass(Class<?> owner, ClassVisitor visitor) {
        String resource = Type.getInternalName(owner) + ".class";
        ClassLoader loader = owner.getClassLoader();
        try (InputStream input = loader == null
            ? ClassLoader.getSystemResourceAsStream(resource) : loader.getResourceAsStream(resource)) {
            if (input == null) throw new GradleException("Cannot read JVM class " + owner.getName());
            new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException exception) {
            throw new GradleException("Cannot inspect JVM class " + owner.getName(), exception);
        }
    }

    private static void rejectStaticTarget(CallableSelector selector, String operation) {
        validateHandlerStatic(selector, false, operation);
    }

    private static void validateHandlerStatic(
        CallableSelector selector, boolean staticHandler, String operation
    ) {
        if (selector.isStatic() != staticHandler) {
            String expected = selector.isStatic() ? operation + "Static" : operation;
            throw new GradleException("Use " + expected + " for "
                + (selector.isStatic() ? "static" : "instance") + " target method "
                + selector.owner().getName() + "::" + selector.name());
        }
    }

    private static Type[] append(Type[] values, Type value) {
        Type[] result = new Type[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = value;
        return result;
    }

    private static Type[] prepend(Type[] values, Type value) {
        Type[] result = new Type[values.length + 1];
        result[0] = value;
        System.arraycopy(values, 0, result, 1, values.length);
        return result;
    }

    private static String functionInvokeDescriptor(int arity) {
        return "(" + "Ljava/lang/Object;".repeat(arity) + ")Ljava/lang/Object;";
    }

    private static void box(MethodVisitor method, Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) return;
        String owner;
        String descriptor;
        switch (type.getSort()) {
            case Type.BOOLEAN -> { owner = "java/lang/Boolean"; descriptor = "(Z)Ljava/lang/Boolean;"; }
            case Type.BYTE -> { owner = "java/lang/Byte"; descriptor = "(B)Ljava/lang/Byte;"; }
            case Type.CHAR -> { owner = "java/lang/Character"; descriptor = "(C)Ljava/lang/Character;"; }
            case Type.SHORT -> { owner = "java/lang/Short"; descriptor = "(S)Ljava/lang/Short;"; }
            case Type.INT -> { owner = "java/lang/Integer"; descriptor = "(I)Ljava/lang/Integer;"; }
            case Type.FLOAT -> { owner = "java/lang/Float"; descriptor = "(F)Ljava/lang/Float;"; }
            case Type.LONG -> { owner = "java/lang/Long"; descriptor = "(J)Ljava/lang/Long;"; }
            case Type.DOUBLE -> { owner = "java/lang/Double"; descriptor = "(D)Ljava/lang/Double;"; }
            default -> throw new GradleException("Unsupported primitive Mixin argument: " + type);
        }
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "valueOf", descriptor, false);
    }

    private static void pushInteger(MethodVisitor method, int value) {
        if (value >= -1 && value <= 5) method.visitInsn(Opcodes.ICONST_0 + value);
        else if (value <= Byte.MAX_VALUE) method.visitIntInsn(Opcodes.BIPUSH, value);
        else if (value <= Short.MAX_VALUE) method.visitIntInsn(Opcodes.SIPUSH, value);
        else method.visitLdcInsn(value);
    }

    private static void clean(Path output) throws IOException {
        if (!Files.isDirectory(output)) return;
        try (var paths = Files.walk(output)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(output)) Files.deleteIfExists(path);
            }
        }
    }

    private record CallableSelector(
        Class<?> owner, String name, String descriptor, boolean isStatic, boolean constructor
    ) {
    }

    private record FieldSelector(Class<?> owner, String name, String descriptor, boolean isStatic) {
    }

    private record BytecodeMethod(int access) {
    }

    private record BytecodeField(int access, String descriptor) {
    }
}
