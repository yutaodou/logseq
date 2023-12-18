(ns frontend.components.settings
  (:require [clojure.string :as string]
            [electron.ipc :as ipc]
            [logseq.shui.core :as shui]
            [frontend.shui :refer [make-shui-context]]
            [frontend.colors :as colors]
            [frontend.components.assets :as assets]
            [frontend.components.conversion :as conversion-component]
            [frontend.components.file-sync :as fs]
            [frontend.components.plugins :as plugins]
            [frontend.components.svg :as svg]
            [frontend.config :as config]
            [frontend.context.i18n :refer [t]]
            [frontend.date :as date]
            [frontend.db :as db]
            [frontend.dicts :as dicts]
            [frontend.handler :as handler]
            [frontend.handler.config :as config-handler]
            [frontend.handler.file-sync :as file-sync-handler]
            [frontend.handler.global-config :as global-config-handler]
            [frontend.handler.notification :as notification]
            [frontend.handler.plugin :as plugin-handler]
            [frontend.handler.route :as route-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.handler.user :as user-handler]
            [frontend.mobile.util :as mobile-util]
            [frontend.modules.instrumentation.core :as instrument]
            [frontend.modules.shortcut.data-helper :as shortcut-helper]
            [frontend.components.shortcut :as shortcut]
            [frontend.spec.storage :as storage-spec]
            [frontend.state :as state]
            [frontend.storage :as storage]
            [frontend.ui :as ui]
            [frontend.util :refer [classnames web-platform?] :as util]
            [frontend.version :refer [version]]
            [goog.object :as gobj]
            [goog.string :as gstring]
            [promesa.core :as p]
            [reitit.frontend.easy :as rfe]
            [rum.core :as rum]))

(defn toggle
  [label-for name state on-toggle & [detail-text]]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for label-for}
    name]
   [:div.rounded-md.sm:max-w-tss.sm:col-span-2
    [:div.rounded-md {:style {:display "flex" :gap "1rem" :align-items "center"}}
     (ui/toggle state on-toggle true)
     detail-text]]])

(rum/defcs app-updater < rum/reactive
  [state version]
  (let [update-pending? (state/sub :electron/updater-pending?)
        {:keys [type payload]} (state/sub :electron/updater)]
    [:span.cp__settings-app-updater

     [:div.ctls.flex.items-center

      [:div.mt-1.sm:mt-0.sm:col-span-2.flex.gap-4.items-center.flex-wrap
       [:div (cond
               (mobile-util/native-android?)
               (ui/button
                 (t :settings-page/check-for-updates)
                 :class "text-sm p-1 mr-1"
                 :href "https://github.com/logseq/logseq/releases")

               (mobile-util/native-ios?)
               (ui/button
                 (t :settings-page/check-for-updates)
                 :class "text-sm p-1 mr-1"
                 :href "https://apps.apple.com/app/logseq/id1601013908")

               (util/electron?)
               (ui/button
                 (if update-pending? (t :settings-page/checking) (t :settings-page/check-for-updates))
                 :class "text-sm p-1 mr-1"
                 :disabled update-pending?
                 :on-click #(js/window.apis.checkForUpdates false))

               :else
               nil)]

       [:div.text-sm.cursor
        {:title    (str (t :settings-page/revision) config/revision)
         :on-click (fn []
                     (notification/show! [:div "Current Revision: "
                                          [:a {:target "_blank"
                                               :href   (str "https://github.com/logseq/logseq/commit/" config/revision)}
                                           config/revision]]
                                         :info
                                         false))}
        version]

       [:a.text-sm.fade-link.underline.inline
        {:target "_blank"
         :href   "https://docs.logseq.com/#/page/changelog"}
        (t :settings-page/changelog)]]]

     (when-not (or update-pending?
                   (string/blank? type))
       [:div.update-state.text-sm
        (case type
          "update-not-available"
          [:p (t :settings-page/app-updated)]

          "update-available"
          (let [{:keys [name url]} payload]
            [:p (str (t :settings-page/update-available))
             [:a.link
              {:on-click
               (fn [e]
                 (js/window.apis.openExternal url)
                 (util/stop e))}
              svg/external-link name " 🎉"]])

          "error"
          [:p (t :settings-page/update-error-1) [:br] (t :settings-page/update-error-2)
           [:a.link
            {:on-click
             (fn [e]
               (js/window.apis.openExternal "https://github.com/logseq/logseq/releases")
               (util/stop e))}
            svg/external-link " release channel"]])])]))

(rum/defc outdenting-hint
  []
  [:div.ui__modal-panel
   {:style {:box-shadow "0 4px 20px 4px rgba(0, 20, 60, .1), 0 4px 80px -8px rgba(0, 20, 60, .2)"}}
   [:div {:style {:margin "12px" :max-width "500px"}}
    [:p.text-sm
     (t :settings-page/preferred-outdenting-tip)
     [:a.text-sm
      {:target "_blank" :href "https://discuss.logseq.com/t/whats-your-preferred-outdent-behavior-the-direct-one-or-the-logical-one/978"}
      (t :settings-page/preferred-outdenting-tip-more)]]
    [:img {:src    "https://discuss.logseq.com/uploads/default/original/1X/e8ea82f63a5e01f6d21b5da827927f538f3277b9.gif"
           :width  500
           :height 500}]]])

(rum/defc auto-expand-hint
  []
  [:div.ui__modal-panel
   {:style {:box-shadow "0 4px 20px 4px rgba(0, 20, 60, .1), 0 4px 80px -8px rgba(0, 20, 60, .2)"}}
   [:div {:style {:margin "12px" :max-width "500px"}}
    [:p.text-sm
     (t :settings-page/auto-expand-block-refs-tip)]
    [:img {:src    "https://user-images.githubusercontent.com/28241963/225818326-118deda9-9d1e-477d-b0ce-771ca0bcd976.gif"
           :width  500
           :height 500}]]])

(defn row-with-button-action
  [{:keys [left-label description action button-label href on-click desc -for stretch center?]
    :or {center? true}}]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4
   {:class (if center? "sm:items-center" "sm:items-start")}
   ;; left column
   [:div.flex.flex-col
    [:label.block.text-sm.font-medium.leading-5.opacity-70
     {:for -for}
     left-label]
    (when description
      [:div.text-xs.text-gray-10 description])]

   ;; right column
   [:div.mt-1.sm:mt-0.sm:col-span-2.flex.items-center
    {:style {:display "flex" :gap "0.5rem" :align-items "center"}}
    [:div {:style (when stretch {:width "100%"})}
     (if action action (shui/button {:text button-label
                                     :href href
                                     :on-click on-click}
                         (make-shui-context)))]
    (when-not (or (util/mobile?)
                  (mobile-util/native-platform?))
      [:div.text-sm.flex desc])]])

(defn edit-config-edn []
  (row-with-button-action
   {:left-label   (t :settings-page/custom-configuration)
    :button-label (t :settings-page/edit-config-edn)
    :href         (rfe/href :file {:path (config/get-repo-config-path)})
    :on-click     ui-handler/toggle-settings-modal!
    :-for         "config_edn"}))

(defn edit-global-config-edn []
  (row-with-button-action
    {:left-label   (t :settings-page/custom-global-configuration)
     :button-label (t :settings-page/edit-global-config-edn)
     :href         (rfe/href :file {:path (global-config-handler/global-config-path)})
     :on-click     ui-handler/toggle-settings-modal!
     :-for         "global_config_edn"}))

(defn edit-custom-css []
  (row-with-button-action
   {:left-label   (t :settings-page/custom-theme)
    :button-label (t :settings-page/edit-custom-css)
    :href         (rfe/href :file {:path (config/get-custom-css-path)})
    :on-click     ui-handler/toggle-settings-modal!
    :-for         "customize_css"}))

(defn edit-export-css []
  (row-with-button-action
   {:left-label   (t :settings-page/export-theme)
    :button-label (t :settings-page/edit-export-css)
    :href         (rfe/href :file {:path (config/get-export-css-path)})
    :on-click     ui-handler/toggle-settings-modal!
    :-for         "export_css"}))

(defn show-brackets-row [t show-brackets?]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for "show_brackets"}
    (t :settings-page/show-brackets)]
   [:div
    [:div.rounded-md.sm:max-w-xs
     (ui/toggle show-brackets?
                config-handler/toggle-ui-show-brackets!
                true)]]
   (when (not (or (util/mobile?) (mobile-util/native-platform?)))
     [:div {:style {:text-align "right"}}
      (ui/render-keyboard-shortcut (shortcut-helper/gen-shortcut-seq :ui/toggle-brackets))])])

(rum/defcs switch-spell-check-row < rum/reactive
  [state t]
  (let [enabled? (state/sub [:electron/user-cfgs :spell-check])]
    [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
     [:label.block.text-sm.font-medium.leading-5.opacity-70
      (t :settings-page/spell-checker)]
     [:div
      [:div.rounded-md.sm:max-w-xs
       (ui/toggle
         enabled?
         (fn []
           (state/set-state! [:electron/user-cfgs :spell-check] (not enabled?))
           (p/then (ipc/ipc :userAppCfgs :spell-check (not enabled?))
                   #(when (js/confirm (t :relaunch-confirm-to-work))
                      (js/logseq.api.relaunch))))
         true)]]]))

(rum/defcs switch-git-auto-commit-row < rum/reactive
  [state t]
  (let [enabled? (state/get-git-auto-commit-enabled?)]
    [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
     [:label.block.text-sm.font-medium.leading-5.opacity-70
      (t :settings-page/git-switcher-label)]
     [:div
      [:div.rounded-md.sm:max-w-xs
       (ui/toggle
         enabled?
         (fn []
           (state/set-state! [:electron/user-cfgs :git/disable-auto-commit?] enabled?)
           (ipc/ipc :userAppCfgs :git/disable-auto-commit? enabled?))
         true)]]]))

(rum/defcs git-auto-commit-seconds < rum/reactive
  [state t]
  (let [secs (or (state/sub [:electron/user-cfgs :git/auto-commit-seconds]) 60)]
    [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
     [:label.block.text-sm.font-medium.leading-5.opacity-70
      (t :settings-page/git-commit-delay)]
     [:div.mt-1.sm:mt-0.sm:col-span-2
      [:div.max-w-lg.rounded-md.sm:max-w-xs
       [:input#home-default-page.form-input.is-small.transition.duration-150.ease-in-out
        {:default-value secs
         :on-blur       (fn [event]
                          (let [value (-> (util/evalue event)
                                          util/safe-parse-int)]
                            (if (and (number? value)
                                     (< 0 value (inc 600)))
                              (do
                                (state/set-state! [:electron/user-cfgs :git/auto-commit-seconds] value)
                                (ipc/ipc :userAppCfgs :git/auto-commit-seconds value))
                              (when-let [elem (gobj/get event "target")]
                                (notification/show!
                                  [:div "Invalid value! Must be a number between 1 and 600."]
                                  :warning true)
                                (gobj/set elem "value" secs)))))}]]]]))

(rum/defc app-auto-update-row < rum/reactive [t]
  (let [enabled? (state/sub [:electron/user-cfgs :auto-update])
        enabled? (if (nil? enabled?) true enabled?)]
    (toggle "usage-diagnostics"
            (t :settings-page/auto-updater)
            enabled?
            #((state/set-state! [:electron/user-cfgs :auto-update] (not enabled?))
              (ipc/ipc :userAppCfgs :auto-update (not enabled?))))))

(defn language-row [t preferred-language]
  (let [on-change (fn [e]
                    (let [lang-code (util/evalue e)]
                      (state/set-preferred-language! lang-code)
                      (ui-handler/re-render-root!)))
        action [:select.form-select.is-small {:value     preferred-language
                                              :on-change on-change}
                (for [language dicts/languages]
                  (let [lang-code (name (:value language))
                        lang-label (:label language)]
                    [:option {:key lang-code :value lang-code} lang-label]))]]
    (row-with-button-action {:left-label (t :language)
                             :-for       "preferred_language"
                             :action     action})))

(defn theme-modes-row [t switch-theme system-theme? dark?]
  (let [color-accent (state/sub :ui/radix-color)
        pick-theme [:ul.theme-modes-options
                    [:li {:on-click (partial state/use-theme-mode! "light")
                          :class    (classnames [{:active (and (not system-theme?) (not dark?))}])} [:i.mode-light {:class (when color-accent "radix")}] [:strong (t :settings-page/theme-light)]]
                    [:li {:on-click (partial state/use-theme-mode! "dark")
                          :class    (classnames [{:active (and (not system-theme?) dark?)}])} [:i.mode-dark {:class (when color-accent "radix")}] [:strong (t :settings-page/theme-dark)]]
                    [:li {:on-click (partial state/use-theme-mode! "system")
                          :class    (classnames [{:active system-theme?}])} [:i.mode-system {:class (when color-accent "radix")}] [:strong (t :settings-page/theme-system)]]]]
    (row-with-button-action {:left-label (t :right-side-bar/switch-theme (string/capitalize switch-theme))
                             :-for       "toggle_theme"
                             :action     pick-theme
                             :desc       (ui/render-keyboard-shortcut (shortcut-helper/gen-shortcut-seq :ui/toggle-theme))})))

(defn accent-color-row []
  (let [color-accent (state/sub :ui/radix-color)
        pick-theme [:div.grid {:style {:grid-template-columns "repeat(5, 1fr)"
                                       :gap "0.75rem"
                                       :width "100%"
                                       :max-width "16rem"}}
                    (for [color colors/color-list
                          :let [active? (= color color-accent)]]
                      [:div.flex.items-center {:style {:height 28}}
                       [:div {:class "w-5 h-5 rounded-full flex justify-center items-center transition ease-in duration-100 hover:cursor-pointer hover:opacity-100"
                              :title color
                              :style {:background-color (colors/variable color :09)
                                      :outline-color (colors/variable color (if active? :07 :06))
                                      :outline-width (if active? "4px" "1px")
                                      :outline-style :solid
                                      :opacity (if active? 1 0.5)}
                              :on-click (fn [_e] (state/set-color-accent! color))}
                        [:div {:class "w-2 h-2 rounded-full transition ease-in duration-100"
                               :style {:background-color (str "var(--rx-" (name color) "-07)")
                                       :opacity (if active? 1 0)}}]]])
                    (when color-accent
                      [:div.col-span-5
                       (shui/button {:text "Back to default color"
                                     :theme :gray
                                     :on-click (fn [_e] (state/unset-color-accent!))}
                                    (make-shui-context nil nil))])]]

    [:<>
     (row-with-button-action {:left-label "Accent color"
                              :description "Choosing an accent color will override any theme you have selected."
                              :-for       "toggle_radix_theme"
                              :stretch    true
                              :action     pick-theme})]))

(defn file-format-row [t preferred-format]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for "preferred_format"}
    (t :settings-page/preferred-file-format)]
   [:div.mt-1.sm:mt-0.sm:col-span-2
    [:div.max-w-lg.rounded-md
     [:select.form-select.is-small
      {:value     (name preferred-format)
       :on-change (fn [e]
                    (let [format (-> (util/evalue e)
                                     (string/lower-case)
                                     keyword)]
                      (user-handler/set-preferred-format! format)))}
      (for [format (map name [:org :markdown])]
        [:option {:key format :value format} (string/capitalize format)])]]]])

(defn date-format-row [t preferred-date-format]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for "custom_date_format"}
    (t :settings-page/custom-date-format)
    (ui/tippy {:html        (t :settings-page/custom-date-format-warning)
               :class       "tippy-hover ml-2"
               :interactive true
               :disabled    false}
              (svg/info))]
   [:div.mt-1.sm:mt-0.sm:col-span-2
    [:div.max-w-lg.rounded-md
     [:select.form-select.is-small
      {:value     preferred-date-format
       :on-change (fn [e]
                    (let [format (util/evalue e)]
                      (when-not (string/blank? format)
                        (config-handler/set-config! :journal/page-title-format format)
                        (notification/show!
                          [:div (t :settings-page/custom-date-format-notification)]
                          :warning false)
                        (state/close-modal!)
                        (route-handler/redirect! {:to :repos}))))}
      (for [format (sort (date/journal-title-formatters))]
        [:option {:key format} format])]]]])

(defn workflow-row [t preferred-workflow]
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for "preferred_workflow"}
    (t :settings-page/preferred-workflow)]
   [:div.mt-1.sm:mt-0.sm:col-span-2
    [:div.max-w-lg.rounded-md
     [:select.form-select.is-small
      {:value     (name preferred-workflow)
       :on-change (fn [e]
                    (-> (util/evalue e)
                        string/lower-case
                        keyword
                        (#(if (= % :now) :now :todo))
                        user-handler/set-preferred-workflow!))}
      (for [workflow [:now :todo]]
        [:option {:key (name workflow) :value (name workflow)}
         (if (= workflow :now) "NOW/LATER" "TODO/DOING")])]]]])

(defn outdenting-row [t logical-outdenting?]
  (toggle "preferred_outdenting"
          [(t :settings-page/preferred-outdenting)
           (ui/tippy {:html        (outdenting-hint)
                      :class       "tippy-hover ml-2"
                      :interactive true
                      :disabled    false}
                     (svg/info))]
          logical-outdenting?
          config-handler/toggle-logical-outdenting!))

(defn showing-full-blocks [t show-full-blocks?]
  (toggle "show_full_blocks"
          (t :settings-page/show-full-blocks)
          show-full-blocks?
          config-handler/toggle-show-full-blocks!))

(defn preferred-pasting-file [t preferred-pasting-file?]
  (toggle "preferred_pasting_file"
          [(t :settings-page/preferred-pasting-file)
           (ui/tippy {:html        (t :settings-page/preferred-pasting-file-hint)
                      :class       "tippy-hover ml-2"
                      :interactive true
                      :disabled    false}
                     (svg/info))]
          preferred-pasting-file?
          config-handler/toggle-preferred-pasting-file!))

(defn auto-expand-row [t auto-expand-block-refs?]
  (toggle "auto_expand_block_refs"
          [(t :settings-page/auto-expand-block-refs)
           (ui/tippy {:html        (auto-expand-hint)
                      :class       "tippy-hover ml-2"
                      :interactive true
                      :disabled    false}
                     (svg/info))]
          auto-expand-block-refs?
          config-handler/toggle-auto-expand-block-refs!))

(defn tooltip-row [t enable-tooltip?]
  (toggle "enable_tooltip"
          (t :settings-page/enable-tooltip)
          enable-tooltip?
          (fn []
            (config-handler/toggle-ui-enable-tooltip!))))

(defn shortcut-tooltip-row [t enable-shortcut-tooltip?]
  (toggle "enable_tooltip"
          (t :settings-page/enable-shortcut-tooltip)
          enable-shortcut-tooltip?
          (fn []
            (state/toggle-shortcut-tooltip!))))

(defn timetracking-row [t enable-timetracking?]
  (toggle "enable_timetracking"
          (t :settings-page/enable-timetracking)
          enable-timetracking?
          #(let [value (not enable-timetracking?)]
             (config-handler/set-config! :feature/enable-timetracking? value))))

(defn update-home-page
  [event]
  (let [value (util/evalue event)]
    (cond
      (string/blank? value)
      (let [home (get (state/get-config) :default-home {})
            new-home (dissoc home :page)]
        (config-handler/set-config! :default-home new-home)
        (notification/show! "Home default page updated successfully!" :success))

      (db/page-exists? value)
      (let [home (get (state/get-config) :default-home {})
            new-home (assoc home :page value)]
        (config-handler/set-config! :default-home new-home)
        (notification/show! "Home default page updated successfully!" :success))

      :else
      (notification/show! (str "The page \"" value "\" doesn't exist yet. Please create that page first, and then try again.") :warning))))

(defn journal-row [enable-journals?]
  (toggle "enable_journals"
          (t :settings-page/enable-journals)
          enable-journals?
          (fn []
            (let [value (not enable-journals?)]
              (config-handler/set-config! :feature/enable-journals? value)))))

(defn enable-all-pages-public-row [t enable-all-pages-public?]
  (toggle "all pages public"
          (t :settings-page/enable-all-pages-public)
          enable-all-pages-public?
          (fn []
            (let [value (not enable-all-pages-public?)]
              (config-handler/set-config! :publishing/all-pages-public? value)))))

;; (defn enable-block-timestamps-row [t enable-block-timestamps?]
;;   (toggle "block timestamps"
;;           (t :settings-page/enable-block-time)
;;           enable-block-timestamps?
;;           (fn []
;;             (let [value (not enable-block-timestamps?)]
;;               (config-handler/set-config! :feature/enable-block-timestamps? value)))))

(defn zotero-settings-row []
  [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
   [:label.block.text-sm.font-medium.leading-5.opacity-70
    {:for "zotero_settings"}
    "Zotero"]
   [:div.mt-1.sm:mt-0.sm:col-span-2
    [:div
     (ui/button
       (t :settings)
       :class "text-sm p-1"
       :style {:margin-top "0px"}
       :on-click
       (fn []
         (state/close-settings!)
         (route-handler/redirect! {:to :zotero-setting})))]]])

(defn auto-push-row [_t current-repo enable-git-auto-push?]
  (when (and current-repo (string/starts-with? current-repo "https://"))
    (toggle "enable_git_auto_push"
            "Enable Git auto push"
            enable-git-auto-push?
            (fn []
              (let [value (not enable-git-auto-push?)]
                (config-handler/set-config! :git-auto-push value))))))

(defn usage-diagnostics-row [t instrument-disabled?]
  (toggle "usage-diagnostics"
          (t :settings-page/disable-sentry)
          (not instrument-disabled?)
          (fn [] (instrument/disable-instrument
                   (not instrument-disabled?)))
          [:span.text-sm.opacity-50 (t :settings-page/disable-sentry-desc)]))

(defn clear-cache-row [t]
  (row-with-button-action {:left-label   (t :settings-page/clear-cache)
                           :button-label (t :settings-page/clear)
                           :on-click     handler/clear-cache!
                           :-for         "clear_cache"}))

(defn version-row [t version]
  (row-with-button-action {:left-label (t :settings-page/current-version)
                           :action     (app-updater version)
                           :-for       "current-version"}))

(defn developer-mode-row [t developer-mode?]
  (toggle "developer_mode"
          (t :settings-page/developer-mode)
          developer-mode?
          (fn []
            (let [mode (not developer-mode?)]
              (state/set-developer-mode! mode)))
          [:div.text-sm.opacity-50 (t :settings-page/developer-mode-desc)]))

(rum/defc plugin-enabled-switcher
  [t]
  (let [value (state/lsp-enabled?-or-theme)
        [on? set-on?] (rum/use-state value)
        on-toggle #(let [v (not on?)]
                     (set-on? v)
                     (storage/set ::storage-spec/lsp-core-enabled v))]
    [:div.flex.items-center.gap-2
     (ui/toggle on? on-toggle true)
     (when (not= (boolean value) on?)
       [:div.relative.opacity-70
        [:span.absolute.whitespace-nowrap
         {:style {:top -18 :left 10}}
         (ui/button (t :plugin/restart)
                    :on-click #(js/logseq.api.relaunch)
                    :small? true :intent "logseq")]])]))

(rum/defc http-server-enabled-switcher
  [t]
  (let [[value _] (rum/use-state (boolean (storage/get ::storage-spec/http-server-enabled)))
        [on? set-on?] (rum/use-state value)
        on-toggle #(let [v (not on?)]
                     (set-on? v)
                     (storage/set ::storage-spec/http-server-enabled v))]
    [:div.flex.items-center.gap-2
     (ui/toggle on? on-toggle true)
     (when (not= (boolean value) on?)
       (ui/button (t :plugin/restart)
                  :on-click #(js/logseq.api.relaunch)
                  :small? true :intent "logseq"))]))

(rum/defc flashcards-enabled-switcher
  [enable-flashcards?]
  (ui/toggle enable-flashcards?
             (fn []
               (let [value (not enable-flashcards?)]
                 (config-handler/set-config! :feature/enable-flashcards? value)))
             true))

(rum/defc user-proxy-settings
  [{:keys [type protocol host port] :as agent-opts}]
  (ui/button [:span.flex.items-center
              [:span.pr-1
               (case type
                 "system" "System Default"
                 "direct" "Direct"
                 (and protocol host port (str protocol "://" host ":" port)))]
              (ui/icon "edit")]
             :class "text-sm p-1"
             :on-click #(state/set-sub-modal!
                          (fn [_] (plugins/user-proxy-settings-panel agent-opts))
                          {:id :https-proxy-panel :center? true})))

(defn plugin-system-switcher-row []
  (row-with-button-action
    {:left-label (t :settings-page/plugin-system)
     :action     (plugin-enabled-switcher t)}))

(defn http-server-switcher-row []
  (row-with-button-action
    {:left-label "HTTP APIs server"
     :action     (http-server-enabled-switcher t)}))

(defn flashcards-switcher-row [enable-flashcards?]
  (row-with-button-action
    {:left-label (t :settings-page/enable-flashcards)
     :action     (flashcards-enabled-switcher enable-flashcards?)}))

(defn https-user-agent-row [agent-opts]
  (row-with-button-action
    {:left-label (t :settings-page/network-proxy)
     :action     (user-proxy-settings agent-opts)}))

(rum/defcs auto-chmod-row < rum/reactive
  [state t]
  (let [enabled? (if (= nil (state/sub [:electron/user-cfgs :feature/enable-automatic-chmod?]))
                   true
                   (state/sub [:electron/user-cfgs :feature/enable-automatic-chmod?]))]
    (toggle
      "automatic-chmod"
      (t :settings-page/auto-chmod)
      enabled?
      #(do
         (state/set-state! [:electron/user-cfgs :feature/enable-automatic-chmod?] (not enabled?))
         (ipc/ipc :userAppCfgs :feature/enable-automatic-chmod? (not enabled?)))
      [:span.text-sm.opacity-50 (t :settings-page/auto-chmod-desc)])))

(defn filename-format-row []
  (row-with-button-action
    {:left-label   (t :settings-page/filename-format)
     :button-label (t :settings-page/edit-setting)
     :on-click     #(state/set-sub-modal!
                      (fn [_] (conversion-component/files-breaking-changed))
                      {:id :filename-format-panel :center? true})}))

(rum/defcs native-titlebar-row < rum/reactive
  [state t]
  (let [enabled? (state/sub [:electron/user-cfgs :window/native-titlebar?])]
    (toggle
      "native-titlebar"
      (t :settings-page/native-titlebar)
      enabled?
      #(when (js/confirm (t :relaunch-confirm-to-work))
         (state/set-state! [:electron/user-cfgs :window/native-titlebar?] (not enabled?))
         (ipc/ipc :userAppCfgs :window/native-titlebar? (not enabled?))
         (js/logseq.api.relaunch))
      [:span.text-sm.opacity-50 (t :settings-page/native-titlebar-desc)])))

(rum/defcs settings-general < rum/reactive
  [_state current-repo]
  (let [preferred-language (state/sub [:preferred-language])
        theme (state/sub :ui/theme)
        dark? (= "dark" theme)
        show-radix-themes? true
        system-theme? (state/sub :ui/system-theme?)
        switch-theme (if dark? "light" "dark")]
    [:div.panel-wrap.is-general
     (version-row t version)
     (language-row t preferred-language)
     (theme-modes-row t switch-theme system-theme? dark?)
     (when (and (util/electron?) (not util/mac?)) (native-titlebar-row t))
     (when show-radix-themes? (accent-color-row))
     (when (config/global-config-enabled?) (edit-global-config-edn))
     (when current-repo (edit-config-edn))
     (when current-repo (edit-custom-css))
     (when current-repo (edit-export-css))]))

(rum/defcs settings-editor < rum/reactive
  [_state current-repo]
  (let [preferred-format (state/get-preferred-format)
        preferred-date-format (state/get-date-formatter)
        preferred-workflow (state/get-preferred-workflow)
        enable-timetracking? (state/enable-timetracking?)
        enable-all-pages-public? (state/all-pages-public?)
        logical-outdenting? (state/logical-outdenting?)
        show-full-blocks? (state/show-full-blocks?)
        preferred-pasting-file? (state/preferred-pasting-file?)
        auto-expand-block-refs? (state/auto-expand-block-refs?)
        enable-tooltip? (state/enable-tooltip?)
        enable-shortcut-tooltip? (state/sub :ui/shortcut-tooltip?)
        show-brackets? (state/show-brackets?)
        enable-git-auto-push? (state/enable-git-auto-push? current-repo)]

    [:div.panel-wrap.is-editor
     (file-format-row t preferred-format)
     (date-format-row t preferred-date-format)
     (workflow-row t preferred-workflow)
     ;; (enable-block-timestamps-row t enable-block-timestamps?)
     (show-brackets-row t show-brackets?)

     (when (util/electron?) (switch-spell-check-row t))
     (outdenting-row t logical-outdenting?)
     (showing-full-blocks t show-full-blocks?)
     (preferred-pasting-file t preferred-pasting-file?)
     (auto-expand-row t auto-expand-block-refs?)
     (when-not (or (util/mobile?) (mobile-util/native-platform?))
       (shortcut-tooltip-row t enable-shortcut-tooltip?))
     (when-not (or (util/mobile?) (mobile-util/native-platform?))
       (tooltip-row t enable-tooltip?))
     (timetracking-row t enable-timetracking?)
     (enable-all-pages-public-row t enable-all-pages-public?)
     (auto-push-row t current-repo enable-git-auto-push?)]))

(rum/defc settings-git
  []
  [:div.panel-wrap
   [:div.text-sm.my-4
    (ui/admonition
      :tip
      [:p (t :settings-page/git-tip)])
    [:span.text-sm.opacity-50.my-4
     (t :settings-page/git-desc-1)]
    [:br] [:br]
    [:span.text-sm.opacity-50.my-4
     (t :settings-page/git-desc-2)]
    [:a {:href "https://git-scm.com/" :target "_blank"}
     "Git"]
    [:span.text-sm.opacity-50.my-4
     (t :settings-page/git-desc-3)]]
   [:br]
   (switch-git-auto-commit-row t)
   (git-auto-commit-seconds t)

   (ui/admonition
     :warning
     [:p (t :settings-page/git-confirm)])])

(rum/defc settings-advanced < rum/reactive
  [current-repo]
  (let [instrument-disabled? (state/sub :instrument/disabled?)
        developer-mode? (state/sub [:ui/developer-mode?])
        https-agent-opts (state/sub [:electron/user-cfgs :settings/agent])]
    [:div.panel-wrap.is-advanced
     (when (and (or util/mac? util/win32?) (util/electron?)) (app-auto-update-row t))
     (usage-diagnostics-row t instrument-disabled?)
     (when-not (mobile-util/native-platform?) (developer-mode-row t developer-mode?))
     (when (util/electron?) (https-user-agent-row https-agent-opts))
     (when (util/electron?) (auto-chmod-row t))
     (when (and (util/electron?) (not (config/demo-graph? current-repo))) (filename-format-row))
     (clear-cache-row t)

     (ui/admonition
       :warning
       [:p (t :settings-page/clear-cache-warning)])]))

(rum/defc sync-enabled-switcher
  [enabled?]
  (ui/toggle enabled?
             (fn []
               (file-sync-handler/set-sync-enabled! (not enabled?)))
             true))

(rum/defc sync-diff-merge-enabled-switcher
  [enabled?]
  (ui/toggle enabled?
             (fn []
               (file-sync-handler/set-sync-diff-merge-enabled! (not enabled?)))
             true))

(defn sync-switcher-row [enabled?]
  (row-with-button-action
    {:left-label (t :settings-page/sync)
     :action     (sync-enabled-switcher enabled?)}))

(defn sync-diff-merge-switcher-row [enabled?]
  (row-with-button-action
    {:left-label (str (t :settings-page/sync-diff-merge) " (Experimental!)") ;; Not included in i18n to avoid outdating translations
     :action     (sync-diff-merge-enabled-switcher enabled?)
     :desc       (ui/tippy {:html        [:div
                                          [:div (t :settings-page/sync-diff-merge-desc)]
                                          [:div (t :settings-page/sync-diff-merge-warn)]]
                            :class       "tippy-hover ml-2"
                            :interactive true
                            :disabled    false}
                           (svg/info))}))

(rum/defc whiteboards-enabled-switcher
  [enabled?]
  (ui/toggle enabled?
             (fn []
               (let [value (not enabled?)]
                 (config-handler/set-config! :feature/enable-whiteboards? value)))
             true))

(defn whiteboards-switcher-row [enabled?]
  (row-with-button-action
    {:left-label (t :settings-page/enable-whiteboards)
     :action     (whiteboards-enabled-switcher enabled?)}))

(rum/defc settings-account-usage-description [pro-account? graph-usage]
  (let [count-usage (count graph-usage)
        count-limit (if pro-account? 10 1)
        default-storage-limit (if pro-account? 10 0.05)
        count-percent (js/Math.round (/ count-usage count-limit 0.01))
        storage-usage (->> (map :used-gbs graph-usage)
                           (reduce + 0))
        storage-usage-formatted (cond
                                  (zero? storage-usage) "0.0"
                                  (< storage-usage 0.01) "Less than 0.01"
                                  :else (gstring/format "%.2f" storage-usage))
        ;; TODO: check logic on this. What are the rules around storage limits?
        ;; do we, and should we be able to, give individual users more storage?
        ;; should that be on a per graph or per user basis?
        storage-limit (->> (range 0 count-limit)
                           (map #(get-in graph-usage [% :limit-gbs] default-storage-limit))
                           (reduce + 0))
        storage-percent (/ storage-usage storage-limit 0.01)
        storage-percent-formatted (gstring/format "%.1f" storage-percent)]
    [:div.text-xs.tracking-wide
     (when pro-account?
       [:<>
        (gstring/format "%s of %s synced graphs " count-usage count-limit)
        [:strong.dark:text-white (gstring/format "(%s%%)" count-percent)]
        ", "])
     (gstring/format "%sGB of %sGB total storage " storage-usage-formatted storage-limit)
     [:strong.dark:text-white (gstring/format "(%s%%)" storage-percent-formatted)]]))

;(rum/defc settings-account-usage-graphs [_pro-account? graph-usage]
;  (when (< 0 (count graph-usage))
;    [:div.grid.gap-3 {:style {:grid-template-columns (str "repeat(" (count graph-usage) ", 1fr)")}}
;     (for [{:keys [name used-percent]} graph-usage
;           :let [color (if (<= 100 used-percent) "bg-red-500" "bg-blue-500")
;                 used-percent' (if (number? used-percent) (* 100 (.toFixed used-percent 2)) 0)]]
;       (ui/tippy
;         {:html  (fn [] [:small.inline-flex.px-2.py-1 (str name " (" used-percent' "%)")])
;          :arrow true}
;         [:div.rounded-full.w-full.h-2.cursor-pointer.overflow-hidden
;          {:class    "bg-black/50"
;           :on-click (fn []
;                       (state/close-modal!)
;                       (route-handler/redirect-to-all-graphs))
;           :tooltip  name}
;          [:div.rounded-full.h-2
;           {:class color
;            :style {:width     (str used-percent' "%")
;                    :max-width "100%"}}]]))]))

(defn gigaBytesFormat
  [bytes]
  (if-let [giga (and (number? bytes) (/ bytes 1024 1024 1024))]
    (if (> giga 1)
      (util/format "%.1fGB" giga)
      (util/format "%fMB" (* giga 1024)))
    bytes))

(rum/defc settings-account-usage-graphs [_pro-account? user-info _graph-usage]
  (when-let [{:keys [GraphCountLimit StorageLimit]} user-info]
    [:span.inline-block.opacity-70.text-sm
     (t :settings-account/sync-usage-tip GraphCountLimit (gigaBytesFormat StorageLimit))]))

(rum/defc ^:large-vars/cleanup-todo settings-account < rum/reactive
  []
  (let [graph-usage (state/get-remote-graph-usage)
        current-graph-uuid (state/sub-current-file-sync-graph-uuid)
        _current-graph-is-remote? ((set (map :uuid graph-usage)) current-graph-uuid)
        refreshing? (state/sub [:ui/loading? :user-fetching?])
        logging-out? (state/sub [:ui/loading? :logging-out?])
        logged-in? (user-handler/logged-in?)
        user-info (state/get-user-info)
        lemon-status (:LemonStatus user-info)
        subscribe-active? (= "active" lemon-status)
        has-subscribed? (some? lemon-status)
        paid-user? (#{"active" "on_trial" "cancelled"} lemon-status)
        gift-user? (some #{"pro"} (:UserGroups user-info))
        pro-account? (or (:ProUser user-info) paid-user? gift-user?)
        expiration-date (some-> user-info :LemonEndsAt date/parse-iso)
        renewal-date (some-> user-info :LemonRenewsAt date/parse-iso)]
    [:div.panel-wrap.is-account.mb-4
     [:div.mt-1.sm:mt-0.sm:col-span-2
      (cond
        logged-in?
        [:div.grid.grid-cols-4.gap-x-2.gap-y-8.pt-2.container-wrap
         [:label (t :settings-account/current-plan)]
         [:div.col-span-3
          [:div.active-plan-card
           [:div.flex.gap-4.items-center.pt-1.justify-between
            (if pro-account?
              [:b.plan-flag (t :settings-account/pro)]
              [:b.plan-flag (t :settings-account/free)])

            [:span
             {:class "relative top-[-4px] flex items-center gap-3"}
             (cond
               (or pro-account? has-subscribed?)
               (ui/button (t :settings-account/manage-plan)
                          {:class      "p-1 h-8 justify-center"
                           :icon       "upload"
                           :href       config/SITE-ACCOUNT-ENTRYPOINT
                           :icon-props {:size 14}})

               ; :on-click user-handler/upgrade})
               (not pro-account?)
               (ui/button (t :settings-account/upgrade-plan)
                          {:class    "p-1 h-8 justify-center"
                           :icon     "upload"
                           :href     config/SITE-ACCOUNT-ENTRYPOINT})
               :else nil)

             [:a.pt-2
              {:class    (when refreshing? "animate-spin")
               :on-click #(when-not refreshing?
                            (state/pub-event! [:user/fetch-info-and-graphs]))}
              (ui/icon "reload")]]]

           (settings-account-usage-graphs pro-account? user-info graph-usage)
           (when pro-account?
             (settings-account-usage-description pro-account? graph-usage))]]

         (when (and has-subscribed? subscribe-active?)
           [:<>
            [:label "Subscription"]

            [:div.col-span-3
             [:a.flex.items-center.gap-1.dark:opacity-40.text-gray-400
              {:href (str config/SITE-ACCOUNT-ENTRYPOINT "/subscriptions")}
              (cond
                ;; If there is no expiration date, print the renewal date
                (and renewal-date (nil? expiration-date))
                [:strong.font-normal (t :settings-account/next-renew-date) ": "
                 (date/get-locale-string renewal-date)]

                ;; If the expiration date is in the future, word it as such
                (< (js/Date.) expiration-date)
                [:strong.font-normal "Pro plan expires on: "
                 (date/get-locale-string expiration-date)]

                ;; Otherwise, ind
                :else
                [:strong.font-normal "Pro plan expired on: "
                 (date/get-locale-string expiration-date)])
              (ui/icon "external-link")]]])

         [:label (t :settings-account/profile)]
         [:div.col-span-3.grid.grid-cols-3.gap-4
          [:div.flex-1.flex.flex-col.gap-2.col-span-1
           [:label.text-sm.font-semibold (t :settings-account/username)]
           [:input.rounded.px-2.py-1.box-border.opacity-60
            {:class    "bg-black/10 dark:bg-black/20"
             :value    (user-handler/username)
             :disabled true}]]

          [:div.flex.flex-col.gap-2.col-span-2
           [:label.text-sm.font-semibold (t :settings-account/email)]
           [:input.rounded.px-2.py-1.box-border.opacity-60
            {:class    "bg-black/10 dark:bg-black/20"
             :disabled true
             :value    (user-handler/email)}]]]

         [:label ""]
         [:div.col-span-3.relative
          {:class "top-[-16px]"}
          [:div.grid.grid-cols-1.gap-4
           [:div.col-span-2
            (ui/button
              [:span.flex.items-center
               (if logging-out? (ui/loading "") (t :logout))]
              {:class    "p-1 h-8 justify-center w-full opacity-60 bg-gray-400 border-none hover:bg-red-400 active:bg-red-600"
               :disabled logging-out?
               :on-click user-handler/logout!
               :icon     (when-not logging-out? "logout")})]

           [:a.text-sm.flex.items-center.opacity-50.space-x-1.hover:opacity-80
            {:href config/SITE-ACCOUNT-ENTRYPOINT :target "_blank"}
            [:b.font-normal (t :settings-account/manage-profile-on-web)]
            (ui/icon "external-link" {:size 14})]]]]

        (not logged-in?)
        [:div.grid.grid-cols-4.gap-4.pt-2.container-wrap
         [:div.col-span-4.flex.flex-wrap.gap-6
          [:div.w-full.text-gray-600.dark:text-gray-300 (t :settings-account/new-desc)]
          [:div.flex-1 (ui/button
                         (t :settings-account/create)
                         {:class    "h-8 w-full text-center justify-center"
                          :on-click (fn []
                                      (state/close-settings!)
                                      (state/pub-event! [:user/login :signUp]))})]
          [:div.flex-1 (ui/button
                         (t :login)
                         {:icon     "login"
                          :class    "h-8 w-full text-center justify-center opacity-80"
                          :intent   "logseq-2"
                          :on-click (fn []
                                      (state/close-settings!)
                                      (state/pub-event! [:user/login]))})]]

         ;; pro plans
         [:div.pro-plan-cards
          [:div.card
           [:div.flag (t :settings-account/free)]
           [:div [:strong.text-xl.font-medium "$0"]]
           [:div.font-semibold (t :settings-account/free-plan-title)]
           [:ul.text-xs.m-0.flex.flex-col.gap-0.5.pl-3.opacity-70
            (t :settings-account/free-plan-desc)]]

          [:div.card
           [:div.flag.pro (t :settings-account/pro)]
           [:div [:strong.text-xl.font-medium "$8"]
            [:span.text-xs.font-base {:class "ml-0.5"} "/ monthly"]]
           [:div.font-semibold (t :settings-account/pro-plan-title)]
           [:ul.text-xs.m-0.flex.flex-col.gap-0.5.pl-3.opacity-70
            (t :settings-account/pro-plan-desc)]]]])]]))

(rum/defc settings-features < rum/reactive
  []
  (let [current-repo (state/get-current-repo)
        enable-journals? (state/enable-journals? current-repo)
        enable-flashcards? (state/enable-flashcards? current-repo)
        enable-sync? (state/enable-sync?)
        enable-sync-diff-merge? (state/enable-sync-diff-merge?)
        enable-whiteboards? (state/enable-whiteboards? current-repo)
        logged-in? (user-handler/logged-in?)]
    [:div.panel-wrap.is-features.mb-8
     (journal-row enable-journals?)
     (when (not enable-journals?)
       [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-center
        [:label.block.text-sm.font-medium.leading-5.opacity-70
         {:for "default page"}
         (t :settings-page/home-default-page)]
        [:div.mt-1.sm:mt-0.sm:col-span-2
         [:div.max-w-lg.rounded-md.sm:max-w-xs
          [:input#home-default-page.form-input.is-small.transition.duration-150.ease-in-out
           {:default-value (state/sub-default-home-page)
            :on-blur       update-home-page
            :on-key-press  (fn [e]
                             (when (= "Enter" (util/ekey e))
                               (update-home-page e)))}]]]])
     (whiteboards-switcher-row enable-whiteboards?)

     (when (and (util/electron?) config/feature-plugin-system-on?)
       (plugin-system-switcher-row))

     (when (util/electron?)
       (http-server-switcher-row))

     (flashcards-switcher-row enable-flashcards?)

     ;; sync
     (when (and (not web-platform?) logged-in?)
       [:<>
        (sync-switcher-row enable-sync?)
        (when enable-sync?
          (sync-diff-merge-switcher-row enable-sync-diff-merge?))])

     ;; zotero
     (zotero-settings-row)

     ;; beta & alpha
     (when (and (not web-platform?) logged-in?
                (user-handler/alpha-or-beta-user?))
       [:div
        [:hr.mt-2.mb-4]
        [:div.it.sm:grid.sm:grid-cols-3.sm:gap-4.sm:items-start
         [:label.flex.font-medium.leading-5.self-start.mt-1
          (ui/icon (if logged-in? "lock-open" "lock") {:class "mr-1"})
          (t :settings-page/beta-features)]]

        [:div.flex.flex-col.gap-4
         [:div.text-base.pt-2.pl-1.opacity-70
          (util/format "🎉 You're a %s user!" (if (user-handler/alpha-user?) "Alpha" "Beta"))]]])]))

(rum/defc settings-effect
  < rum/static
  [active]

  (rum/use-effect!
    (fn []
      (let [active (and (sequential? active) (name (first active)))
            ^js ds (.-dataset js/document.body)]
        (if active
          (set! (.-settingsTab ds) active)
          (js-delete ds "settingsTab"))
        #(js-delete ds "settingsTab")))
    [active])

  [:<>])

(rum/defcs settings
  < (rum/local [:general :general] ::active)
    {:will-mount
     (fn [state]
       (state/load-app-user-cfgs)
       state)
     :did-mount
     (fn [state]
       (let [active-tab (first (:rum/args state))
             *active (::active state)]
         (when (keyword? active-tab)
           (reset! *active [active-tab nil]))
         (when-let [^js el (rum/dom-node state)]
           (some-> (.querySelector el "aside.cp__settings-aside")
                   (.focus))))
       state)
     :will-unmount
     (fn [state]
       (state/close-settings!)
       state)}
    rum/reactive
  [state _active-tab]
  (let [current-repo (state/sub :git/current-repo)
        ;; enable-block-timestamps? (state/enable-block-timestamps?)
        _installed-plugins (state/sub :plugin/installed-plugins)
        plugins-of-settings (and config/lsp-enabled? (seq (plugin-handler/get-enabled-plugins-if-setting-schema)))
        *active (::active state)]

    [:div#settings.cp__settings-main
     (settings-effect @*active)
     [:div.cp__settings-inner {:class "min-h-[65dvh] max-h-[75dvh]"}
      [:aside.cp__settings-aside
       {:style     {:min-width "13rem"}
        :tab-index "-1"
        :auto-focus "on"
        :on-key-down (fn [^js e]
                       (let [up? (= (.-key e) "ArrowUp")
                             down? (= (.-key e) "ArrowDown")]
                         (when (or up? down?)
                           (when-let [^js active (some-> (.-target e) (.querySelector ".settings-menu-item.active"))]
                             (when-let [^js target (if down? (.-nextSibling active) (.-previousSibling active))]
                               (let [active (.. active -dataset -id)
                                     target (.. target -dataset -id)]
                                 (reset! *active (map keyword [target active]))))))))}
       [:header.cp__settings-header
        [:h1.cp__settings-modal-title (t :settings)]]
       [:ul.settings-menu
        (for [[label id text icon]
              [[:general "general" (t :settings-page/tab-general) (ui/icon "adjustments")]
               [:account "account" (t :settings-page/tab-account) (ui/icon "user-circle")]
               [:editor "editor" (t :settings-page/tab-editor) (ui/icon "writing")]
               [:keymap "keymap" (t :settings-page/tab-keymap) (ui/icon "keyboard")]

               (when (util/electron?)
                 [:git "git" (t :settings-page/tab-git) (ui/icon "history")])

               ;; (when (util/electron?)
               ;;   [:assets "assets" (t :settings-page/tab-assets) (ui/icon "box")])

               [:advanced "advanced" (t :settings-page/tab-advanced) (ui/icon "bulb")]
               [:features "features" (t :settings-page/tab-features) (ui/icon "app-feature")]

               (when plugins-of-settings
                 [:plugins "plugins" (t :settings-of-plugins) (ui/icon "puzzle")])]]

          (when label
            [:li.settings-menu-item
             {:key      text
              :data-id  id
              :class    (util/classnames [{:active (= label (first @*active))}])
              :on-click #(reset! *active [label (first @*active)])}

             [:a.flex.items-center.settings-menu-link icon [:strong text]]]))]]

      (let [active-label (first @*active)]
        [:article
         [:header.cp__settings-header
          [:h1.cp__settings-category-title
           (when-not (= :plugins active-label)
             (t (keyword (str "settings-page/tab-" (name (first @*active))))))]]

         (case active-label
           :plugins
           (let [label (second @*active)]
             (state/pub-event! [:go/plugins-settings (:id (first plugins-of-settings))])
             (reset! *active [label label])
             nil)

           :general
           (settings-general current-repo)

           :account
           (settings-account)

           :editor
           (settings-editor current-repo)

           :git
           (settings-git)

           :keymap
           (shortcut/shortcut-keymap-x)

           :assets
           (assets/settings-content)

           :advanced
           (settings-advanced current-repo)

           :features
           (settings-features)

           nil)])]]))
