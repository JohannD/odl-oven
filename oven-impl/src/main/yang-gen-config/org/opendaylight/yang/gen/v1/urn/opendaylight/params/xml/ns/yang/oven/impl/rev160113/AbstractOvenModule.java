/*
* Generated file
*
* Generated from: yang module name: oven-impl yang module local name: oven
* Generated by: org.opendaylight.controller.config.yangjmxgenerator.plugin.JMXGenerator
* Generated at: Fri Jan 15 14:31:42 EST 2016
*
* Do not modify this file unless it is present under src/main directory
*/
package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113;
@org.opendaylight.yangtools.yang.binding.annotations.ModuleQName(namespace = "urn:opendaylight:params:xml:ns:yang:oven:impl", name = "oven-impl", revision = "2016-01-13")

public abstract class AbstractOvenModule extends org.opendaylight.controller.config.spi.AbstractModule<AbstractOvenModule> implements org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.OvenModuleMXBean,org.opendaylight.controller.config.api.RuntimeBeanRegistratorAwareModule {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.AbstractOvenModule.class);

    //attributes start

    public static final org.opendaylight.controller.config.api.JmxAttribute rpcRegistryJmxAttribute = new org.opendaylight.controller.config.api.JmxAttribute("RpcRegistry");
    private javax.management.ObjectName rpcRegistry; // mandatory

    public static final org.opendaylight.controller.config.api.JmxAttribute brokerJmxAttribute = new org.opendaylight.controller.config.api.JmxAttribute("Broker");
    private javax.management.ObjectName broker; // mandatory

    public static final org.opendaylight.controller.config.api.JmxAttribute notificationServiceJmxAttribute = new org.opendaylight.controller.config.api.JmxAttribute("NotificationService");
    private javax.management.ObjectName notificationService; // mandatory

    //attributes end

    public AbstractOvenModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public AbstractOvenModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,AbstractOvenModule oldModule,java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    private org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.OvenRuntimeRegistrator rootRuntimeBeanRegistratorWrapper;

    public org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.OvenRuntimeRegistrator getRootRuntimeBeanRegistratorWrapper(){
        return rootRuntimeBeanRegistratorWrapper;
    }

    @Override
    public void setRuntimeBeanRegistrator(org.opendaylight.controller.config.api.runtime.RootRuntimeBeanRegistrator rootRuntimeRegistrator){
        this.rootRuntimeBeanRegistratorWrapper = new org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.OvenRuntimeRegistrator(rootRuntimeRegistrator);
    }

    @Override
    public void validate() {
        dependencyResolver.validateDependency(org.opendaylight.controller.config.yang.md.sal.binding.RpcProviderRegistryServiceInterface.class, rpcRegistry, rpcRegistryJmxAttribute);
        dependencyResolver.validateDependency(org.opendaylight.controller.config.yang.md.sal.binding.BindingAwareBrokerServiceInterface.class, broker, brokerJmxAttribute);
        dependencyResolver.validateDependency(org.opendaylight.controller.config.yang.md.sal.binding.NotificationProviderServiceServiceInterface.class, notificationService, notificationServiceJmxAttribute);

        customValidation();
    }

    protected void customValidation() {
    }

    private org.opendaylight.controller.sal.binding.api.RpcProviderRegistry rpcRegistryDependency;
    protected final org.opendaylight.controller.sal.binding.api.RpcProviderRegistry getRpcRegistryDependency(){
        return rpcRegistryDependency;
    }private org.opendaylight.controller.sal.binding.api.BindingAwareBroker brokerDependency;
    protected final org.opendaylight.controller.sal.binding.api.BindingAwareBroker getBrokerDependency(){
        return brokerDependency;
    }private org.opendaylight.controller.sal.binding.api.NotificationProviderService notificationServiceDependency;
    protected final org.opendaylight.controller.sal.binding.api.NotificationProviderService getNotificationServiceDependency(){
        return notificationServiceDependency;
    }

    protected final void resolveDependencies() {
        brokerDependency = dependencyResolver.resolveInstance(org.opendaylight.controller.sal.binding.api.BindingAwareBroker.class, broker, brokerJmxAttribute);
        rpcRegistryDependency = dependencyResolver.resolveInstance(org.opendaylight.controller.sal.binding.api.RpcProviderRegistry.class, rpcRegistry, rpcRegistryJmxAttribute);
        notificationServiceDependency = dependencyResolver.resolveInstance(org.opendaylight.controller.sal.binding.api.NotificationProviderService.class, notificationService, notificationServiceJmxAttribute);
    }

    public boolean canReuseInstance(AbstractOvenModule oldModule){
        // allow reusing of old instance if no parameters was changed
        return isSame(oldModule);
    }

    public java.lang.AutoCloseable reuseInstance(java.lang.AutoCloseable oldInstance){
        // implement if instance reuse should be supported. Override canReuseInstance to change the criteria.
        return oldInstance;
    }

    public boolean isSame(AbstractOvenModule other) {
        if (other == null) {
            throw new IllegalArgumentException("Parameter 'other' is null");
        }
        if (java.util.Objects.deepEquals(rpcRegistry, other.rpcRegistry) == false) {
            return false;
        }
        if(rpcRegistry!= null) {
            if (!dependencyResolver.canReuseDependency(rpcRegistry, rpcRegistryJmxAttribute)) { // reference to dependency must be reusable as well
                return false;
            }
        }
        if (java.util.Objects.deepEquals(broker, other.broker) == false) {
            return false;
        }
        if(broker!= null) {
            if (!dependencyResolver.canReuseDependency(broker, brokerJmxAttribute)) { // reference to dependency must be reusable as well
                return false;
            }
        }
        if (java.util.Objects.deepEquals(notificationService, other.notificationService) == false) {
            return false;
        }
        if(notificationService!= null) {
            if (!dependencyResolver.canReuseDependency(notificationService, notificationServiceJmxAttribute)) { // reference to dependency must be reusable as well
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractOvenModule that = (AbstractOvenModule) o;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    // getters and setters
    @Override
    public javax.management.ObjectName getRpcRegistry() {
        return rpcRegistry;
    }

    @Override
    @org.opendaylight.controller.config.api.annotations.RequireInterface(value = org.opendaylight.controller.config.yang.md.sal.binding.RpcProviderRegistryServiceInterface.class)
    public void setRpcRegistry(javax.management.ObjectName rpcRegistry) {
        this.rpcRegistry = rpcRegistry;
    }

    @Override
    public javax.management.ObjectName getBroker() {
        return broker;
    }

    @Override
    @org.opendaylight.controller.config.api.annotations.RequireInterface(value = org.opendaylight.controller.config.yang.md.sal.binding.BindingAwareBrokerServiceInterface.class)
    public void setBroker(javax.management.ObjectName broker) {
        this.broker = broker;
    }

    @Override
    public javax.management.ObjectName getNotificationService() {
        return notificationService;
    }

    @Override
    @org.opendaylight.controller.config.api.annotations.RequireInterface(value = org.opendaylight.controller.config.yang.md.sal.binding.NotificationProviderServiceServiceInterface.class)
    public void setNotificationService(javax.management.ObjectName notificationService) {
        this.notificationService = notificationService;
    }

    public org.slf4j.Logger getLogger() {
        return LOGGER;
    }

}