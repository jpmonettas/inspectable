(ns inspectable.ui.themes)

(def themes
  {:light {:tree-background "white"
           :problem-background "#CCCCCC"
           :problem-foreground "red"
           :ok-node-foreground "#CCCCCC"
           :header-background "#f2dede"
           :header-foreground "#a94442"
           :browser-link-foreground "#3399ff"
           }
   :dark {
          }})

(defn get-color [k]
  (get-in themes [:light k]))
