(ns greenwood.xyz
  "Utilities for determining chunk position in XYZ files and processing
  chunks using reducers.

  Uses iota, which uses mmap()."
  (:refer-clojure :exclude [* - + == /])
  (:use clojure.core.matrix)
  (:use clojure.core.matrix.operators)
  (:require [clojure.core.reducers :as r]
            [clojure.string :as strng]
            [greenwood.basics :as basic]
            [greenwood.utils :as utils]
            [greenwood.mol :as jmol]
            iota))


(defn atom-pos [mol]
  "This will associate file positions to the :pos of each atom (starting with zero).
Usage: (atoms-pos mol)"
    (map (fn [x y] (assoc-in x [:pos] y)) mol (iterate inc 0)))



(defn- step-chunk-starts
  "chad"
  ([] [(vector-of :int) 0 :find-start])
  ([[v i state]] (conj v i))
  ([[v i state] ^String l]
    (case state
      :find-start (if (re-matches #"[ \t]*\d+[ \t]*" l)
                    [(conj v i) (inc i) :comment]
                    [v (inc i) :find-start])
      :comment [v (inc i) :find-start])))


(defn chunk-starts
  "Returns `[chunk-start-line ... total-num-of-lines]` from a reducible
  `lines` of XYZ file lines."
  [lines]
  (->> lines
       (r/reduce step-chunk-starts)
       (step-chunk-starts)))


(defn chunk-ranges
  "Returns `[[start-idx0 end-idx0] [end-idx0 start-idx1] ...]`.
  Same as `(partition 2 1 start+total)` but non-lazy.

  Designed to produce arguments for subvec to grab groups of items at a time."
  [start+total]
  (->> (partition 2 1 start+total)
       (reduce (fn [ranges [s e]] (conj! ranges (vector-of :int s e)))
         (transient []))
       (persistent!)))


(defn index-xyz*
  "Return a vec of vecs, each of which is a range of indexes in coll which
  constitutes a single XYZ chunk. Retrieve the chunks with
  `(map #(apply subvec coll %) (index-xyz* coll))`.
  This "
  [coll]
  (->> coll
       chunk-starts
       chunk-ranges))


(defn index-xyz
  "Returns the chunk index of an XYZ file.  This can be used to cache the
  index or even write it to disk for iota to read later.  This would be useful
  for very large files.  It would allow for copying the xyz file and index file to
  several machines and processing the file in parallel."
  [xyzfile]
  (index-xyz* (iota/seq xyzfile)))



(defn natoms-index
  "Returns the chunk index of an XYZ file assuming that each time step has
  the same number of atoms.  The user will also need to determine the number
  of lines in the xyz file before running.  This will greatly speed up the
  reading of the the xyz-file.
  Usage: (natoms-index 3 10) => ((0 5) (5 10))"
  [natoms nlines]
  (partition 2 1 (range 0 (inc nlines) (+ 2 natoms))))






(defn reax-index-timesteps
  [start stop howoften natoms]
 (partition 2 1 (map int (range (* (/ start howoften) (+ 2 natoms))
  (* ((comp inc inc)  (/ stop howoften)) (+ 2 natoms)) (+ 2 natoms)))))






(defn foldable-chunks*
  "Return a foldable collection of the chunks in coll."
  ([coll]
    (foldable-chunks* coll (index-xyz* coll)))
  ([coll index]
    (r/map (fn [[s e]] (subvec coll s e)) index)))



(defn foldable-chunks
  "Return a foldable collection chunks in an XYZ file.
  index-xyz can be used to precompute an index of the
  chunk ranges.  Which can then be stored for later
  consumation of the xyzfile."
  ([xyzfile]
    (foldable-chunks* (iota/vec xyzfile)))
  ([xyzfile index]
    (foldable-chunks* (iota/vec xyzfile) index)))










(defn take-nth-foldable-chunks
  "Return a foldable collection chunks in an XYZ file.
  index-xyz can be used to precompute an index of the
  chunk ranges.  Which can then be stored for later
  consumation of the xyzfile.

  Returns a lazy seq of every nth item in index."
  ([xyzfile n]
    (foldable-chunks* (iota/vec xyzfile) (take-nth n (index-xyz xyzfile))))
  ([xyzfile index n]
    (foldable-chunks* (iota/vec xyzfile) (take-nth n index))))


(defn take-foldable-chunks
  "Return a foldable collection chunks in an XYZ file.
  index-xyz can be used to precompute an index of the
  chunk ranges.  Which can then be stored for later
  consumation of the xyzfile.

  Returns a lazy seq of every nth item in index."
  ([xyzfile n]
    (foldable-chunks* (iota/vec xyzfile) (take n (index-xyz xyzfile))))
  ([xyzfile index n]
    (foldable-chunks* (iota/vec xyzfile) (take n index))))








(comment
  "Silly example: count all the chunks."
  (->> (foldable-chunks "myfile.xyz")
       (r/map (constantly 1))
       (r/fold +))
  "Same."
  (->> (foldable-chunks "myfile.xyz")
       (r/fold + (fn ([] 0) ([x _] (inc x)))))
  "Get comment line of each chunk."
  (->> (foldable-chunks "myfile.xyz")
       (r/map (fn [atom-count comment & atoms] comment))
       (r/foldcat)))




(defn xyz-iota->atoms
  "This will parse a string into the atoms struct.  Note that the string should start
with the first atom, not with the number of atoms in the system.  Also, this
assumes that there is a newline character between atoms.

Thus if: (def test 'C 0 0 0 \n C 0.3333 0.6667 0')
then the usage would be (xyz-str->atoms test)."
 ([lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix :double-array  (map read-string (take 3 (rest %)))) nil nil nil nil y)
                (strng/split (strng/triml x) #"\s+")))
 lines (iterate inc 0)))
 ([charge-column lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix :double-array  (map read-string (take 3 (rest %))))  (double (read-string (nth % charge-column))) nil nil nil y)
                (strng/split (strng/triml x) #"\s+")))
     lines (iterate inc 0))))


(defn xyz-iota->atoms_readable
  "This will parse a string into the atoms struct.  Note that the string should start
with the first atom, not with the number of atoms in the system.  Also, this
assumes that there is a newline character between atoms.

Thus if: (def test 'C 0 0 0 \n C 0.3333 0.6667 0')
then the usage would be (xyz-str->atoms test)."
  ([lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix (map read-string (take 3 (rest %)))) nil nil nil nil y)
                (strng/split (strng/triml x) #"\s+")))
 lines (iterate inc 0)))
 ([charge-column lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix (map read-string (take 3 (rest %))))  (double (read-string (nth % charge-column))) nil nil nil y)
                (strng/split (strng/triml x) #"\s+")))
     lines (iterate inc 0))))




(defn xyz-str->atoms
  "This will parse a string into the atoms struct.  Note that the string should start
with the first atom, not with the number of atoms in the system.  Also, this
assumes that there is a newline character between atoms.

Thus if: (def test 'C 0 0 0 \n C 0.3333 0.6667 0')
then the usage would be (xyz-str->atoms test)."
  [string]
  (let [lines (strng/split-lines string)]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix :double-array  (map read-string (take 3 (rest %)))) nil  nil nil nil y)
                (strng/split (strng/trim x) #"\s+")))
                  lines (iterate inc 0))))


(defn xyz-str->atoms_readable
  "This will parse a string into the atoms struct.  Note that the string should start
with the first atom, not with the number of atoms in the system.  Also, this
assumes that there is a newline character between atoms.

Thus if: (def test 'C 0 0 0 \n C 0.3333 0.6667 0')
then the usage would be (xyz-str->atoms test)."
  [string]
  (let [lines (strng/split-lines string)]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix  (map read-string (take 3 (rest %)))) nil  nil nil nil y)
                (strng/split (strng/trim x) #"\s+")))
                  lines (iterate inc 0))))







(defn xyz-reax-iota->atoms
  "This will parse a set of strings into the atoms struct.
The assumption is that the set of lines are from a reaxff xmolout file, where ixmolo was
set such that the molecule number was printed out in the 5th column of every line.
Note that the string should start with the first atom, not with the number of atoms
in the system.  Also, this assumes that there is a newline character between atoms."
  ([lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix :double-array  (map read-string (take 3 (rest %))))  nil nil nil (read-string (nth % 4)) y)
                (strng/split (strng/triml x) #"\s+")))
     lines (iterate inc 0)))
  ([charge-column lines]
    (mapv (fn [x y] (#(basic/new-atom (.intern (first %)) (matrix :double-array  (map read-string (take 3 (rest %))))  (read-string (nth % charge-column)) nil nil (read-string (nth % 4)) y)
                (strng/split (strng/triml x) #"\s+")))
     lines (iterate inc 0))))




(defn parse-xyz
  "This reads the whole xyz-file into memory, thus this should be used only for small files.
  Since there are a number of files types that are based on the xyz-file system where they
  include additional data (ie. charge, velocity, strain) we are allowing for charge to also
  be read in; but in order to do that you will have to specify which column the charge is
  placed in.

This produces a col of cols, where each of the sub-cols is a time step.

Usage: (second (parse-xyz PATH)), where PATH is a string containing the path
to some xyz file.

  Usage: (second (parse-xyz PATH 5)), where PATH is a string containing the path
to some xyz file, and the charge is given in the 5 column.  The column number starts
  counting from one."
  ([filename]
  (->> (foldable-chunks filename)
       (r/map (partial drop 2))
       (r/map xyz-iota->atoms_readable)
       (into [])))
  ([filename charge-column]
  (->> (foldable-chunks filename)
       (r/map (partial drop 2))
       (r/map (partial xyz-iota->atoms_readable charge-column))
       (into []))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;  xmolout
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reaxff-cell-params-lvs-
  "Cell parameters should be banned!  But since they are not, and Reaxff uses them,
  and like all other codes that use them, has its own particular method for defining
  their directions"
  [a b c alpha beta gamma]
  (let [alph (* 0.0174532925199433 alpha)
        bet (* 0.0174532925199433 beta)
        gamm (* 0.0174532925199433 gamma)
        cosphi (/ (- (cos gamm) (* (cos alph) (cos bet))) (sin alph)  (sin bet))
        cphi (if (> cosphi 1) 1 cosphi)
        sinphi  (pow (- 1 (* cphi cphi)) 0.5)]
    [[(* a (sin bet) sinphi) (* a (sin bet) cosphi) (* a (cos bet))]
     [0  (* b (sin alph)) (* b (cos alph))]
     [0 0 c]]))


(defn parse-xmolout
  ([lines]
  (let [x (as-> lines x
                (second x)
                (strng/split x #"[ X]+"))]
  (basic/system (first x)
          (read-string (second x))
          (apply reaxff-cell-params-lvs- (map read-string (drop 3 x)))
          (xyz-reax-iota->atoms (drop 2 lines)))))
  ([charge-column lines]
  (let [x (as-> lines x
                (second x)
                (strng/split x #"[ X]+"))]
  (basic/system (first x)
          (read-string (second x))
          (apply reaxff-cell-params-lvs- (map read-string (drop 3 x)))
          (xyz-reax-iota->atoms charge-column (drop 2 lines))))))




(defn parse-xmoloutt
  ([lines]
  (let [x (as-> lines x
                (second x)
                (strng/split x #"[ X]+"))]

          (map read-string (drop 3 x)))))






;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(defn get-atoms [atoms]
  "get atoms is used to turn a mol into a col of cols where the subcols are nothing
more than vectors of the species and the coordinates values.  In this function
we also test to see if the mol has values for the :pos key, if all of the atoms
do then we sort the mol by :pos."
  (let [sorted-atoms (if (not-any? (comp nil? :pos) atoms)
                       (sort-by :pos atoms)
                       atoms)
	f2 (comp #(concat (map float (take 3 %)) (drop 3 %)) :coordinates)]
    (map #(cons (:species %) (f2 %)) sorted-atoms)))




#_(defn write-xyz [mol]
  "Returns a seq of atoms as a string in xyz format.  This version of write-xyz
allows for writing a whole bunch of time steps (arranged in a col of cols of maps)
or a single time step which is a col of maps.

Usage:  Suppose (def test (xyz-str->atoms 'C 0 0 0 \n C 0.3333 0.6667 0')) then
the following would both give the same result (write-xyz test) => '2\n\n C 0 0 0 \n C 0.3333 0.6667 0'."
(str (count mol)  "\n\n" (utils/inter-cat-tree ["\n" "   "] (get-atoms mol)) "\n"))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn parse-geo-HETATM-
  "This is a helper function that will be used in parse-geo that will parse the species
  and positions of all the atoms assocated with one BIOGRF/XTLGRF record in a geo file."
  [x]
  (->> x
        (strng/split-lines )
        (utils/grep #"HETATM" )
        (map (comp
               #(basic/new-atom (second %) (map read-string (take 3 (rest (rest %)))) nil nil nil nil (read-string (first %)))
               rest
               #(strng/split % #"\s+")) )))



(defn- parse-geo-CONECT-
  "This is a helper function that will be used in parse-geo that will parse the species
  and positions of all the atoms assocated with one BIOGRF/XTLGRF record in a geo file."
  [x]
  (let [l (->> x
       (strng/split-lines )
       (utils/grep #"CONECT" )
       (rest ))]
    (if (empty? l)
      (repeat nil)
       (map (comp
              #(basic/neigh-struct (map (comp dec read-string) %) nil nil)
               (partial drop 2)
              #(strng/split % #"\s+")) l))))




(defn parse-geo
"This is currently not full featured, and will only read in the positions of the atoms."
[filename]
  (map #(jmol/col->mol (parse-geo-HETATM- %) :neigh (parse-geo-CONECT- %))
    (utils/lazy-chunk-file filename #"BIOGRF|XTLGRF")))



(defn parse-geo
"This is currently not full featured, and will only read in the positions of the atoms."
[filename]
  (map parse-geo-HETATM-
    (utils/lazy-chunk-file filename #"BIOGRF|XTLGRF")))





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



#_(->> (foldable-chunks "/Volumes/HAWAII/DESORPTION/NEGATIVEPRESSURE/C2F1256ud400MPa/N3/xmolout")
(r/map (partial drop 2))
     (r/map xyz-iota->atoms)
       (r/map #(shift [0 0 -200] %) )
       (r/map out/write-xyz)
     (r/map #(utils/append-file "/Users/chadjunkermeier/Desktop/graphene2.xyz" %))
     (into []))





;(require '[greenwood.atomic-structure-output :as out])
#_(->> (foldable-chunks "/Users/chadjunkermeier/Desktop/graphene.xyz" )
(r/map (partial drop 2))
     (r/map xyz-iota->atoms)
     (r/map out/write-xyz)
     (r/map #(utils/append-file "/Users/chadjunkermeier/Desktop/graphene2.xyz" %))
     (into []))

;(parse-xyz "/Users/chadjunkermeier/Dropbox/Fgraphene-NEB/Results/NEB/FF/F0F12/F0F12.xyz")

#_(->> (foldable-chunks "/Volumes/HAWAII/DESORPTION/NEGATIVEPRESSURE/all300MPa/N10/xmolout" (reax-index-timesteps 1532000 1532100 100 (* 2 576)))
     (r/map parse-xmolout)
     (r/map :mol)
     (r/map (partial dfdf {:name 2} ))
     (r/map write-xyz)
     (r/map #(utils/append-file "/Users/chadjunkermeier/Desktop/graphene.xyz" %))
       (into []))

#_(->> (take-foldable-chunks "/Users/chadjunkermeier/Desktop/xmolout" 1)
(r/map (partial drop 2))
     (r/map xyz-iota->atoms)
     (into []))


