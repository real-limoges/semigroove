;;; Directory Local Variables for cantor
;;; See: M-x describe-variable RET cider-clojure-cli-aliases

;; Always jack in with the :dev alias so the REPL gets dev/user.clj (boot!)
;; on the classpath AND the arm64 JVM opts (--enable-native-access, the
;; java.desktop/com.apple.eawt --add-opens). Without :dev, (boot!) is missing
;; and Overtone starts unclean on Apple silicon.
((clojure-mode . ((cider-clojure-cli-aliases . ":dev"))))
