(ns tilde.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [alandipert.storage-atom :refer [local-storage]]
              [cljs-uuid.core :as uuid]
              [goog.events :as events])
     (:import [goog.events KeyHandler]
              [goog.events.KeyHandler EventType]))

(enable-console-print!)

(defonce app-state (atom {:players {}}))

(def firebase (js/Firebase. "https://burning-heat-936.firebaseio.com/players"))
(def prefs (local-storage (atom {}) :prefs))

(defn player-id [] (or (:player-id @prefs) (swap! prefs assoc :player-id (uuid/make-random))))

(defn coords []
  (let [[x y] (:coords @prefs)]
    [(or x 0) (or y 0)]))

(defn message []
  (or (:message @prefs) ""))

(defn speed []
  4)

(defn player-name []
  (or (:name @prefs) "rfb"))

(defn player-view [[id {:keys [name message]
                        [x y] :coords
                        :as player}] owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {"position" "absolute" "top" (+ 30 y) "left" x}}
               (dom/div #js {:style #js {"position" "relative" "left" 9 "top" -3 "font-family" "monospace" "color" "grey" "font-size" "50%" }} name)
               (dom/div #js {:style #js {"position" "absolute" "left" 0 "top" 0 "font-family" "monospace" "background-color" (str "#"(.toString (rand-int 16rFFFFFF) 16)) "width" "5px" "height" "5px"}} "")
               (dom/div #js {:style #js {"position" "relative" "font-family" "monospace"}} message)))))

(defn push-current-user []
  (.push firebase (clj->js {:id (str (player-id))
                            :name (player-name)
                            :coords (coords)
                            :message (message)})))

(defn bound-x [x]
  (-> x
      (max 0)
      (min 1000)))

(defn bound-y [y]
  (-> y
      (max 0)
      (min 1000)))

(defn handle-update-name [e app owner]
  (swap! prefs assoc :name (-> e .-target .-value))
  (push-current-user))

(defn handle-update-message [e app owner]
  (when (== (.-which e) 13)
    (swap! prefs assoc :message (-> e .-target .-value))
    (push-current-user)))

(defn update-coords! [move-x move-y]
  (swap! prefs (fn [{[x y] :coords :as prefs}]
                 (assoc prefs :coords [(bound-x (move-x x)) (bound-y (move-y y))])))
  (push-current-user))

(defn fwd [x]
  (+ x (speed)))

(defn back [x]
  (- x (speed)))

(defn start []
  (om/root
   (fn [{:keys [current-player players] :as app} owner]
     (reify
       om/IRender
       (render [_]
         (apply dom/div nil
                (dom/input
                 #js {:value (player-name)
                      :placeholder "name"
                      :onChange #(handle-update-name % app owner)})
                (dom/input
                 #js {:placeholder "message" :ref "message"
                      :onKeyDown #(handle-update-message % app owner)})
                (om/build-all player-view players)))
       om/IWillMount
       (will-mount [_]
         (-> firebase
             (.limit 100)
             (.on "child_added" (fn [snapshot]
                                  (let [{:keys [id name coords] :as updated-player} (js->clj (.val snapshot) :keywordize-keys true)]
                                    (om/update! players id updated-player)))))
         (events/listen (KeyHandler. js/document) EventType.KEY
                        (fn [event]
                          (case (.-keyCode event)
                            37 (update-coords! back identity)
                            38 (update-coords! identity back)
                            39 (update-coords! fwd identity)
                            40 (update-coords! identity fwd)
                            ;;191 (.focus (om/get-node owner "message"))
                            nil))))))
   app-state
   {:target (js/document.getElementById "app")})
  (push-current-user))

(set! (.-onload js/window) start)
