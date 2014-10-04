(ns tilde.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]))

(defonce app-state (atom {:text "Hello Chestnut!"}))



(defn start []
  (om/root
   (fn [app owner]
     (reify om/IRender
       (render [_]
         (dom/body
          (dom/h1 nil (:text app))
          (dom/img #js {:src "http://www.animatedgif.net/underconstruction/const_e0.gif"} "")))))
   app-state
   {:target js/window.document.body}))

(set! (.-onload js/window) start)
