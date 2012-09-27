(ns pallet.crate.zookeeper-test
  (:use pallet.crate.zookeeper)
  (:use clojure.test)
  (:require
   [pallet.action :as action]
   [pallet.action.exec-script :as exec-script]
   [pallet.action.package :as package]
   [pallet.action.service :as service]
   [pallet.build-actions :as build-actions]
   [pallet.compute :as compute]
   [pallet.core :as core]
   [pallet.crate.automated-admin-user :as automated-admin-user]
   [pallet.crate.java :as java]
   [pallet.crate.network-service :as network-service]
   [pallet.live-test :as live-test]
   [pallet.parameter :as parameter]
   [pallet.phase :as phase]
   [pallet.test-utils :as test-utils]))

(deftest zookeeper-test
  (is                                   ; just check for compile errors for now
   (build-actions/build-actions
    {:server {:group-name "tag"
              :image {:os-family :ubuntu}
              :node (test-utils/make-node "tag")}}
    (zookeeper-settings {})
    (install-zookeeper)
    (zookeeper-config)
    (zookeeper-init))))


(deftest live-test
  (doseq [image live-test/*images*]
    (live-test/test-nodes
     [compute node-map node-types]
     {:zookeeper
      {:image image
       :count 1
       :phases {:bootstrap (phase/phase-fn
                            (package/minimal-packages)
                            (package/package-manager :update)
                            (automated-admin-user/automated-admin-user))
                :settings (phase/phase-fn
                           (java/java-settings {})
                           (zookeeper-settings {}))
                :configure (phase/phase-fn
                            (java/install-java)
                            (install-zookeeper)
                            (zookeeper-config)
                            (zookeeper-init)
                            (service/service "zookeeper" :action :restart))
                :verify (phase/phase-fn
                         (network-service/wait-for-port-listen 2181)
                         (exec-script/exec-checked-script
                          "check zookeeper"
                          (println "zookeeper ruok")
                          (pipe (println "ruok") ("nc" -q 2 "localhost" 2181))
                          (println "zookeeper stat ")
                          (pipe (println "stat") ("nc" -q 2 "localhost" 2181))
                          (println "zookeeper dump ")
                          (pipe (println "dump") ("nc" -q 2 "localhost" 2181))
                          (println "zookeeper imok ")
                          (= "imok"
                             @(pipe (println "ruok")
                                    ("nc" -q 2 "localhost" 2181)))))}}}
     (core/lift (:zookeeper node-types) :phase :verify :compute compute))))
