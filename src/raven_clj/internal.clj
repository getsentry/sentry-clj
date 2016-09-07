(ns raven-clj.internal
  "Hacks on hacks on hacks on hacks again.

  Some weird shit required to get the Raven lib to do the right thing."
  (:require [cheshire.factory :as fac]
            [cheshire.generate :as gen])
  (:import (com.getsentry.raven DefaultRavenFactory)
           (com.getsentry.raven.event.interfaces SentryInterface)
           (com.getsentry.raven.marshaller.json InterfaceBinding)))

(defrecord CljInterface [^String interface-name ^Object value]
  SentryInterface
  (getInterfaceName [_]
    (name interface-name)))

(defrecord CljInterfaceBinding []
  InterfaceBinding
  (writeInterface [_ jg interface]
    (gen/generate jg (:value interface) fac/default-date-format nil nil)))

(def factory
  (proxy [DefaultRavenFactory] []
    (createMarshaller [dsn]
      (let [marshaller (proxy-super createMarshaller dsn)]
        (.addInterfaceBinding marshaller CljInterface
                              (->CljInterfaceBinding))
        marshaller))))
