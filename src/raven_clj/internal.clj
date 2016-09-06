(ns raven-clj.internal
  "Hacks on hacks on hacks on hacks again.

  Some weird shit required to get the Raven lib to do the right thing."
  (:require [cheshire.factory :as fac]
            [cheshire.generate :as gen]))

;; A Raven interface with an arbitrary name and value.

(gen-class :name com.getsentry.raven.event.interfaces.CljInterface
           :implements [com.getsentry.raven.event.interfaces.SentryInterface]
           :init init
           :state state
           :constructors {[clojure.lang.Keyword Object] []})

(defn -init
  [interface-name value]
  [[] [(name interface-name) value]])

(defn -getInterfaceName
  [this]
  (first (.state this)))

;; An interface binding for the interface which uses Cheshire to serialize the
;; value.

(gen-class :name com.getsentry.raven.marshaller.json.CljInterfaceBinding
           :implements [com.getsentry.raven.marshaller.json.InterfaceBinding])

(defn -writeInterface
  [_ jg interface]
  (gen/generate jg (second (.state interface)) fac/default-date-format nil nil))

;; An extended RavenFactory class which adds the interface binding to new
;; marshallers.

(gen-class :name com.getsentry.raven.CljRavenFactory
           :extends com.getsentry.raven.DefaultRavenFactory
           :exposes-methods {createMarshaller createOldMarshaller})

(defn -createMarshaller
  [this dsn]
  (let [marshaller (.createOldMarshaller this dsn)]
    (.addInterfaceBinding marshaller
                          com.getsentry.raven.event.interfaces.CljInterface
                          (com.getsentry.raven.marshaller.json.CljInterfaceBinding.))
    marshaller))

;; I really wish the raven-java folks had just used Jackson's built-in object
;; mapper.
