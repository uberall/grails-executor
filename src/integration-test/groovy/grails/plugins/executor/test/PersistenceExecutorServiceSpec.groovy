/*
 * Copyright 2010 Robert Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.executor.test

import executor.test.Book
import grails.plugins.executor.test.Book
import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Integration
@Rollback
class PersistenceExecutorServiceSpec extends Specification {

    static transactional = false

    def sessionFactory
    def executorService

    void setup() {
        Book.withNewSession {
            Book.list()*.delete(flush: true)
            1.upto(5) { new Book(name: "$it").save(flush: true) }
        }
    }

    void cleanup() {
        Book.list()*.delete(flush: true)
    }

    def "test execute"() {
        expect:
        Book.runAsyncFired.get()
        5 == Book.count()

        when:
        def latch = new CountDownLatch(1)
        executorService.execute {
            assert 5 == Book.count()
            Book.list()*.delete()
            new Book(name: "async book").save()
            latch.countDown()
        }

        waitFor "end of book delete task", latch
        //sleep for a second to wait for the thread to finish and the session to flush
        sleep(1000)

        then:
        1 == Book.count()
    }

    def "test submit callable"() {
        expect:
        5 == Book.count()

        when:
        def latch = new CountDownLatch(1)

        def closure = {
            assert 5 == Book.count()
            sleep(2000) //give it a couple of seconds in here so we can test stuff
            Book.list()*.delete()
            def book = new Book(name: "async book").save()
            latch.countDown()
            return book
        }

        Future future = executorService.submit(closure as Callable)
        then:
        //this should fire while the submited callable task is still running still show 5
        5 == Book.count()

        when:
        waitFor "end of callable", latch, 4l
        //just to make sure we are good this thread before the other finishes
        new Book(name: "normal book").save()

        def fbook = future.get()
        then:
        //this will sit here and wait for the submited callable to finish and return the value.
         "async book" == fbook.name
        2 == Book.count()
    }

    def "test submit runnable"() {
        when:
        def latch = new CountDownLatch(1)

        Future future = executorService.submit({
            //Book.withTransaction {
            Book.list()*.delete()
            //}
            latch.countDown()
        } as Runnable)

        waitFor "end of runnable", latch

        def fbook = future.get()
        then:
        //this will return a null since we sumbmited a runnable
        fbook == null
        0 == Book.count()
    }

    def "test submit Runnable with return"() {
        when:
        def latch = new CountDownLatch(1)
        def clos = {
            Book.list()*.delete()
            latch.countDown()
        }

        Future future = executorService.submit(clos, "nailed it")
        waitFor "end of runnable", latch

        def fbook = future.get()
        then:
        //this will return a null since we sumbmited a runnable
         "nailed it" == fbook
        0 == Book.count()

    }

    static void waitFor(String message, CountDownLatch latch, long timeout = 1l) {
        if (!latch.await(timeout, TimeUnit.SECONDS)) {
            throw new Exception("Timed out waiting for $message, $latch.count more latch countDown should have run")
        }
    }

}
