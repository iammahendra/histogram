(ns histogram.test.core
  (:import [java.lang Math]
           [java.util Random])
  (:use [histogram.core]
        [clojure.test]))

(defn- about= [v1 v2 range]
  (>= range (Math/abs (double (- v1 v2)))))

(defn- normal-data [size]
  (let [^Random rnd (Random.)]
    (repeatedly size #(.nextGaussian rnd))))

(defn- rand-data [size]
  (repeatedly size #(rand)))

(defn- cat-data [size]
  (repeatedly size
              #(let [v (rand)]
                 [v (cond (< v (/ 1 3)) :apple
                          (< v (/ 2 3)) :orange
                          :else :grape)])))

(defn- group-data [size]
  (map list (repeatedly #(rand)) (cat-data size)))

(deftest sum-test
  (let [points 10000]
    (is (about= (sum (reduce insert! (create) (normal-data points)) 0)
                (/ points 2)
                (/ points 100)))))

(deftest median-test
  (let [points 10000]
    (is (about= (median (reduce insert! (create) (rand-data points)))
                0.5 0.05))
    (is (about= (median (reduce insert! (create) (normal-data points)))
                0 0.05))))

(deftest merge-test
  (is (empty? (bins (merge! (create) (create)))))
  (is (seq (bins (merge! (insert! (create) 1) (create)))))
  (is (seq (bins (merge! (create) (insert! (create) 1)))))
  (let [points 1000
        hist-count 10
        hists (repeatedly hist-count
                          #(reduce insert! (create) (normal-data points)))
        merged-hist (reduce merge! hists)]
    (is (about= (sum merged-hist 0)
                (/ (* points hist-count) 2)
                (/ (* points hist-count) 100)))))

(deftest mixed-test
  (let [insert-pair #(apply insert! (apply insert! (create) %1) %2)
        vals [[1] [1 2] [1 :a] [1 [:a]]]]
    (doseq [x vals y vals]
      (if (= x y)
        (is (insert-pair x y))
        (is (thrown? Throwable (insert-pair x y)))))))

(deftest density-test
  (let [hist (reduce insert! (create) [1 2 2 3])]
    (is (= 0.0 (density hist 0.0)))
    (is (= 0.5 (density hist 0.5)))
    (is (= 1.0 (density hist 1.0)))
    (is (= 1.5 (density hist 1.5)))
    (is (= 1.5 (density hist 2.0)))
    (is (= 1.5 (density hist 2.5)))
    (is (= 1.0 (density hist 3.0)))
    (is (= 0.5 (density hist 3.5)))
    (is (= 0.0 (density hist 4.0)))))

(deftest categorical-test
  (let [points 10000
        hist (reduce (fn [h [x y]] (insert! h x y))
                     (create)
                     (cat-data points))
        ext-sum (extended-sum hist 0.5)]
    (is (about= (:apple (:target ext-sum))
                (/ points 3)
                (/ points 50)))
    (is (about= (:orange (:target ext-sum))
                (/ points 6)
                (/ points 50)))))

(deftest group-test
  (let [points 10000
        hist (reduce (fn [h [x y]] (insert! h x y))
                     (create)
                     (group-data points))
        ext-sum (extended-sum hist 0.5)]
    (is (about= (first (:target ext-sum))
                (/ points 4)
                (/ points 100)))
    (is (about= (:orange (second (:target ext-sum)))
                (/ points 6)
                (/ points 100)))))

(deftest weighted-gap-test
  (let [points 10000
        weighted (bins (reduce insert! (create 32 true) (normal-data points)))
        classic (bins (reduce insert! (create 32 false) (normal-data points)))]
    (> (+ (:count (first weighted) (last weighted)))
       (+ (:count (first classic) (last classic))))))