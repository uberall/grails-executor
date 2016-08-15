package grails.plugins.executor

import grails.core.GrailsApplication
import grails.plugins.Plugin

import java.util.concurrent.Executors

class ExecutorGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.1 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "layouts/**/**",
            "grails/plugins/executor/test/**/**"
    ]

    def title = "Concurrency / asynchronous / background process plugin"
    def author = "Joshua Burnett"
    def authorEmail = "joshua@greenbill.com"
    def description = "its all concurrent baby."
    def profiles = ['web']
    def loadAfter = ['core', 'hibernate']

    def license = "APACHE"
    def organization = [name: "uberall GmbH", url: "https://uberall.com"]
    def developers = [
            [name: "Florian Langenhahn", email: "florian.langenhahn@uberall.com"]
    ]
    def issueManagement = [system: 'GITHUB', url: 'https://github.com/uberall/grails-executor/issues']
    def documentation = "http://github.com/uberall/grails-executor"
    def scm = [url: 'https://github.com/uberall/grails-executor']

    Closure doWithSpring() { {->
        // create the main executorService backed by a cached thread pool
        executorService(PersistenceContextExecutorWrapper) { bean ->
            bean.destroyMethod = 'destroy'
            persistenceInterceptor = ref("persistenceInterceptor")
            executor = Executors.newCachedThreadPool()
        }
        }
    }

    void doWithDynamicMethods() {
        for (artifactClasses in [grailsApplication.controllerClasses, grailsApplication.serviceClasses, grailsApplication.domainClasses]) {
            for (clazz in artifactClasses) {
                addAsyncMethods(grailsApplication, clazz)
            }
        }
    }

    void onChange(Map<String, Object> event) {
        if (grailsApplication.isControllerClass(event.source) || grailsApplication.isServiceClass(event.source)) {
            addAsyncMethods(grailsApplication, event.source)
        }
    }

    private static void addAsyncMethods(GrailsApplication application, clazz) {
        clazz.metaClass.runAsync = { Runnable runnable ->
            application.mainContext.executorService.withPersistence(runnable)
        }
        clazz.metaClass.callAsync = { Closure closure ->
            application.mainContext.executorService.withPersistence(closure)
        }
        clazz.metaClass.callAsync = { Runnable runnable, returnval ->
            application.mainContext.executorService.withPersistence(runnable, returnval)
        }
    }

}
