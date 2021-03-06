(ns greenwood.testsystems
  (:use [greenwood.xyz :only [ xyz-str->atoms]]
        [greenwood.neighbors :only [neighbors]]))


(def h2o-ts
  (neighbors (xyz-str->atoms "O 0 0 0\nH 0.757 0.586 0.0\nH -0.757, 0.586, 0.0") 0.2 1.4))

(def water (neighbors (xyz-str->atoms "O 0 0 0\nH 0.757 0.586 0.0\nH -0.757, 0.586, 0.0") 0.2 1.4))

(def hydroxyl (neighbors (xyz-str->atoms "O 0 0 0\nH 0 0 0.96") 0.2 1.4))
(def hydroxyl2 (xyz-str->atoms "O 0 0 0\nH 0 0 -0.96"))

(def oxygen (xyz-str->atoms "O 0 0 0"))
(def fluorine (xyz-str->atoms "F 0 0 0"))

(def lvs-ts [[40 0 0][0 40 0][0 0 40]])

(def dihedral1
  (neighbors (xyz-str->atoms "H 1 0 0\nO 0 0 0\nO 0 0 1\nH 0 1 1") 0.2 1.4))

(def H2 (neighbors (xyz-str->atoms "H 0 0 0\nH 0.74 0 0") 0.2 1.1))

(def F2 (neighbors (xyz-str->atoms "F 0 0 0\nF 1.4119 0 0") 0.2 1.1))

(def benzene (neighbors (xyz-str->atoms "C        0.00000        1.40272        0.00000
  H        0.00000        2.49029        0.00000
  C       -1.21479        0.70136        0.00000
  H       -2.15666        1.24515        0.00000
  C       -1.21479       -0.70136        0.00000
  H       -2.15666       -1.24515        0.00000
  C        0.00000       -1.40272        0.00000
  H        0.00000       -2.49029        0.00000
  C        1.21479       -0.70136        0.00000
  H        2.15666       -1.24515        0.00000
  C        1.21479        0.70136        0.00000
  H        2.15666        1.24515        0.00000") 0.2 1.4))

(def benzene (xyz-str->atoms "C        0.00000        1.40272        0.00000
  H        0.00000        2.49029        0.00000
  C       -1.21479        0.70136        0.00000
  H       -2.15666        1.24515        0.00000
  C       -1.21479       -0.70136        0.00000
  H       -2.15666       -1.24515        0.00000
  C        0.00000       -1.40272        0.00000
  H        0.00000       -2.49029        0.00000
  C        1.21479       -0.70136        0.00000
  H        2.15666       -1.24515        0.00000
  C        1.21479        0.70136        0.00000
  H        2.15666        1.24515        0.00000") )

 (def amine
 (xyz-str->atoms
 "N	2.48724 2.8720173670784095	0.0
 H	2.48724 1.9326133670784094	0.592145
 H	2.48720 3.8114213	0.592145"))

