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

(defn player-id []
  (or (:player-id @prefs)
      (swap! prefs assoc :player-id (uuid/make-random))))

(defn coords []
  (let [[x y] (:coords @prefs)]
    [(or x 0) (or y 0)]))

(defn message []
  (or (:message @prefs) ""))

(defn color []
  (or (:color @prefs)
      (swap! prefs assoc :color (str "#"(.toString (rand-int 16rFFFFFF) 16)))))

(defn speed []
  4)

(defn player-name []
  (or (:name @prefs) "rfb"))

(defn player-view [[id {:keys [name message color]
                        [x y] :coords
                        :as player}] owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {"position" "absolute" "top" (+ 40 y) "left" x}}
               (dom/div #js {:style #js {"position" "relative" "left" 9 "top" -3 "font-family" "monospace" "color" "grey" "font-size" "50%" }} name)
               (dom/div #js {:style #js {"position" "absolute" "left" 0 "top" 0 "font-family" "monospace" "background-color" (or color "red") "width" "5px" "height" "5px"}} "")
               (dom/div #js {:style #js {"position" "relative" "font-family" "monospace"}} message)))))

(defn push-current-user []
  (.push firebase (clj->js {:id (str (player-id))
                            :name (player-name)
                            :coords (coords)
                            :color (color)
                            :message (message)})))

(defn bound-x [x]
  (mod x 1000))

(defn bound-y [y]
  (mod y 1000))

(defn handle-update-name [e app owner]
  (swap! prefs assoc :name (-> e .-target .-value))
  (push-current-user))

(defn handle-update-color [e app owner]
  (swap! prefs assoc :color (-> e .-target .-value))
  (push-current-user))

(defn handle-update-message [e app owner]
  (when (== (.-which e) 13)
    (let [target (.-target e)]
     (swap! prefs assoc :message (.-value target))
     (set! (.-value target) ""))
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
                (dom/input
                 #js {:placeholder "color"
                      :onChange #(handle-update-color % app owner)})
                (dom/span #js {:style #js {"color" "grey" "margin-left" "5px"}} "use your arrow keys to move around - hit return in the message box to post a message")
                (om/build-all player-view players)))
       om/IWillMount
       (will-mount [_]
         (-> firebase
             (.limit 1000)
             (.on "child_added" (fn [snapshot]
                                  (let [{:keys [id name coords] :as updated-player} (js->clj (.val snapshot) :keywordize-keys true)]
                                    (om/update! players id updated-player)))))
         (events/listen (KeyHandler. js/document) EventType.KEY
                        (fn [event]
                          (case (.-keyCode event)
                            37 (do (update-coords! back identity) (.preventDefault event))
                            38 (do (update-coords! identity back) (.preventDefault event))
                            39 (do (update-coords! fwd identity) (.preventDefault event))
                            40 (do  (update-coords! identity fwd) (.preventDefault event))
                            ;;191 (.focus (om/get-node owner "message"))
                            nil))))))
   app-state
   {:target (js/document.getElementById "app")})
  (push-current-user))

(set! (.-onload js/window) start)
