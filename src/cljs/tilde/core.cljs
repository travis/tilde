(ns tilde.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [alandipert.storage-atom :refer [local-storage]]))

(defonce app-state (atom {}))


(def Player (js/Parse.Object.extend "Player"))


(def prefs (local-storage (atom {}) :prefs))

(defn current-player [{:keys [success error]}]
  (if (:player-id @prefs)
    (-> (js/Parse.Query. Player)
        (.get (:player-id @prefs)
              #js {:success success
                   :error error}))
    (-> (Player.)
        (.save nil #js {:success (fn [p]
                                   (js/console.log p)
                                   (swap! prefs assoc :player-id (.-id p))
                                   (success p))
                        :error (fn [a b] (js/console.log "ham"))}))))

(defn coords [player]
  (or (.get "coordinates") [0 0]))

(defn start []
  (js/Parse.initialize "9lmgVVGTJrDB75WqukerRJHPmOTU13SZgeaoD2ue", "D6WCZZIfb26dZ8C03Z8W7kSDwEK2piBcPHcpOMRF")

  (om/root
   (fn [{:keys [player players] :as app} owner]
     (reify
       om/IRender
       (render [_]
         (dom/div nil (str player)))
       om/IWillMount
       (will-mount [_]
         (current-player {:success (fn [player]
                                     (om/update! app :player player)
                                     (js/console.log player)
                                     (js/console.log "hihihi"))
                          :error js/console.log})
         (-> (js/Parse.Query. Player)
             (.find #js {:success (fn [players] (om/update! app :players players))
                         :error (fn [] (js/console.log "error loading players!"))})))))
   app-state
   {:target (js/document.getElementById "app")}))

(set! (.-onload js/window) start)
