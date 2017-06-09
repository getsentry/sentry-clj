(ns sentry-clj.internal
  "Hacks on hacks on hacks on hacks again.

  Some weird shit required to get the Sentry lib to do the right thing."
  (:require [cheshire.factory :as fac]
            [cheshire.generate :as gen])
  (:import (io.sentry SentryClientFactory DefaultSentryClientFactory)
           (io.sentry.dsn Dsn)
           (io.sentry.event.interfaces SentryInterface)
           (io.sentry.marshaller.json JsonMarshaller
                                      InterfaceBinding)))

(defrecord CljInterface [^String interface-name ^Object value]
  SentryInterface
  (getInterfaceName [_]
    interface-name))

(defrecord CljInterfaceBinding []
  InterfaceBinding
  (writeInterface [_ jg interface]
    (gen/generate jg (:value interface) fac/default-date-format nil nil)))

(def ^SentryClientFactory factory
  (proxy [DefaultSentryClientFactory] []
    (createMarshaller [^Dsn dsn]
      (let [^JsonMarshaller marshaller (proxy-super createMarshaller dsn)]
        (.addInterfaceBinding marshaller CljInterface (->CljInterfaceBinding))
        marshaller))))
