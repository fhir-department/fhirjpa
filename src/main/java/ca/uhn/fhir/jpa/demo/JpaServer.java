package ca.uhn.fhir.jpa.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.JpaConformanceProviderDstu2;
import ca.uhn.fhir.jpa.provider.JpaSystemProviderDstu2;
//import ca.uhn.fhir.jpa.provider.SubscriptionTriggeringProvider;
import ca.uhn.fhir.jpa.provider.SubscriptionTriggeringProvider;
import ca.uhn.fhir.jpa.provider.dstu3.JpaConformanceProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.JpaSystemProviderDstu3;
import ca.uhn.fhir.jpa.provider.dstu3.TerminologyUploaderProviderDstu3;
import ca.uhn.fhir.jpa.provider.r4.JpaConformanceProviderR4;
import ca.uhn.fhir.jpa.provider.r4.JpaSystemProviderR4;
import ca.uhn.fhir.jpa.search.DatabaseBackedPagingProvider;
//import ca.uhn.fhir.jpa.subscription.resthook.SubscriptionRestHookInterceptor;
import ca.uhn.fhir.model.dstu2.composite.MetaDt;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.ETagSupportEnum;
import ca.uhn.fhir.rest.server.HardcodedServerAddressStrategy;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;
import java.util.List;

public class JpaServer extends RestfulServer {

    private static final long serialVersionUID = 1L;
    private static final Logger ourLog = LoggerFactory.getLogger(JpaServer.class);

    private WebApplicationContext myAppCtx;

    @SuppressWarnings("unchecked")
    @Override
    protected void initialize() throws ServletException {
        super.initialize();

        /*
         * We want to support FHIR DSTU2 format. This means that the server
         * will use the DSTU2 bundle format and other DSTU2 encoding changes.
         *
         * If you want to use DSTU1 instead, change the following line, and change the 2 occurrences of dstu2 in web.xml to dstu1
         */
        FhirVersionEnum fhirVersion = FhirVersionEnum.R4;
        setFhirContext(new FhirContext(fhirVersion));

        // Get the spring context from the web container (it's declared in web.xml)
        myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();

        /*
         * The BaseJavaConfigDstu2.java class is a spring configuration
         * file which is automatically generated as a part of hapi-fhir-jpaserver-base and
         * contains bean definitions for a resource provider for each resource type
         */
        String resourceProviderBeanName;
        if (fhirVersion == FhirVersionEnum.DSTU2) {
            resourceProviderBeanName = "myResourceProvidersDstu2";
        } else if (fhirVersion == FhirVersionEnum.DSTU3) {
            resourceProviderBeanName = "myResourceProvidersDstu3";
        } else if (fhirVersion == FhirVersionEnum.R4) {
            resourceProviderBeanName = "myResourceProvidersR4";
        } else {
            throw new IllegalStateException();
        }
        List<IResourceProvider> beans = myAppCtx.getBean(resourceProviderBeanName, List.class);
        setResourceProviders(beans);

        /*
         * The system provider implements non-resource-type methods, such as
         * transaction, and global history.
         */
        Object systemProvider;
        if (fhirVersion == FhirVersionEnum.DSTU2) {
            systemProvider = myAppCtx.getBean("mySystemProviderDstu2", JpaSystemProviderDstu2.class);
        } else if (fhirVersion == FhirVersionEnum.DSTU3) {
            systemProvider = myAppCtx.getBean("mySystemProviderDstu3", JpaSystemProviderDstu3.class);
        } else if (fhirVersion == FhirVersionEnum.R4) {
            systemProvider = myAppCtx.getBean("mySystemProviderR4", JpaSystemProviderR4.class);
        } else {
            throw new IllegalStateException();
        }
        setPlainProviders(systemProvider);

        /*
         * The conformance provider exports the supported resources, search parameters, etc for
         * this server. The JPA version adds resource counts to the exported statement, so it
         * is a nice addition.
         */
        if (fhirVersion == FhirVersionEnum.DSTU2) {
            IFhirSystemDao<ca.uhn.fhir.model.dstu2.resource.Bundle, MetaDt> systemDao = myAppCtx.getBean("mySystemDaoDstu2", IFhirSystemDao.class);
            JpaConformanceProviderDstu2 confProvider = new JpaConformanceProviderDstu2(this, systemDao,
                    myAppCtx.getBean(DaoConfig.class));
            confProvider.setImplementationDescription("CLAIMCDR-FHIRJPA");
            setServerConformanceProvider(confProvider);
        } else if (fhirVersion == FhirVersionEnum.DSTU3) {
            IFhirSystemDao<Bundle, Meta> systemDao = myAppCtx.getBean("mySystemDaoDstu3", IFhirSystemDao.class);
            JpaConformanceProviderDstu3 confProvider = new JpaConformanceProviderDstu3(this, systemDao,
                    myAppCtx.getBean(DaoConfig.class));
            confProvider.setImplementationDescription("CLAIMCDR-FHIRJPA");
            setServerConformanceProvider(confProvider);
        } else if (fhirVersion == FhirVersionEnum.R4) {
            IFhirSystemDao<org.hl7.fhir.r4.model.Bundle, org.hl7.fhir.r4.model.Meta> systemDao = myAppCtx.getBean("mySystemDaoR4", IFhirSystemDao.class);
            JpaConformanceProviderR4 confProvider = new JpaConformanceProviderR4(this, systemDao,
                    myAppCtx.getBean(DaoConfig.class));
            confProvider.setImplementationDescription("CLAIMCDR-FHIRJPA");
            setServerConformanceProvider(confProvider);
        } else {
            throw new IllegalStateException();
        }

        /*
         * Enable ETag Support (this is already the default)
         */
        setETagSupport(ETagSupportEnum.ENABLED);

        /*
         * This server tries to dynamically generate narratives
         */
        FhirContext ctx = getFhirContext();
        ctx.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());

        /*
         * Default to JSON and pretty printing
         */
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(EncodingEnum.JSON);

        /*
         * -- New in HAPI FHIR 1.5 --
         * This configures the server to page search results to and from
         * the database, instead of only paging them to memory. This may mean
         * a performance hit when performing searches that return lots of results,
         * but makes the server much more scalable.
         */
        setPagingProvider(myAppCtx.getBean(DatabaseBackedPagingProvider.class));

        /*
         * This interceptor formats the output using nice colourful
         * HTML output when the request is detected to come from a
         * browser.
         */
        ResponseHighlighterInterceptor responseHighlighterInterceptor = myAppCtx.getBean(ResponseHighlighterInterceptor.class);
        this.registerInterceptor(responseHighlighterInterceptor);

        /*
         * If you are hosting this server at a specific DNS name, the server will try to
         * figure out the FHIR base URL based on what the web container tells it, but
         * this doesn't always work. If you are setting links in your search bundles that
         * just refer to "localhost", you might want to use a server address strategy:
         */
        if (false) { // <-- DISABLED RIGHT NOW
            setServerAddressStrategy(new HardcodedServerAddressStrategy("http://mydomain.com/fhir/baseDstu3"));
        }

        /*
         * If you are using DSTU3+, you may want to add a terminology uploader, which allows
         * uploading of external terminologies such as Snomed CT. Note that this uploader
         * does not have any security attached (any anonymous user may use it by default)
         * so it is a potential security vulnerability. Consider using an AuthorizationInterceptor
         * with this feature.
         */
        if (false) { // <-- DISABLED RIGHT NOW
            if (fhirVersion == FhirVersionEnum.DSTU3) {
                registerProvider(myAppCtx.getBean(TerminologyUploaderProviderDstu3.class));
            }
        }

        /*
         * If you want to support subscriptions, you will want to register interceptors for
         * any type of subscriptions you want to support.
         */
//        boolean subscriptionsEnabled = false;
//        if (subscriptionsEnabled) { // <-- DISABLED RIGHT NOW
//            SubscriptionRestHookInterceptor restHookInterceptor = myAppCtx.getBean(SubscriptionRestHookInterceptor.class);
//            registerInterceptor(restHookInterceptor);
//
//            // You might alo want to enable other subscription interceptors too
//            // eg...
//            // myAppCtx.getBean(SubscriptionWebsocketInterceptor.class);
//            // myAppCtx.getBean(SubscriptionEmailInterceptor.class);
//
//            // If you want to enable the $trigger-subscription operation to allow
//            // manual triggering of a subscription delivery, enable this provider
//            SubscriptionTriggeringProvider retriggeringProvider = myAppCtx.getBean(SubscriptionTriggeringProvider.class);
//            registerProvider(retriggeringProvider);
//        }

    }

}