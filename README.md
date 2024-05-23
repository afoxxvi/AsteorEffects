# AsteorEffects

A particle lib for Paper servers.

## Usage
All features are contained in `AsteorEffect.kt`, copy it into your project.
```kotlin
//Obtain an effect object
val effect = AsteorEffect.create(Particle.REDSTONE, location)

//Set properties
effect.count(10).offset(1.0).dustPotions(Color.AQUA)

//Show
effect.drawCircle(3.0, 24)

//Inline
AsteorEffect.create(Particle.REDSTONE, location).count(10).offset(1.0).dustOptions(Color.AQUA).drawCircle(3.0, 24)
```

## Current effect patterns(static)
- single
- circle
- line
- polygon
- star
- sphere
- vortex
- triangle
- parallelogram

## Use in java plugins
1. Convert `AsteorEffect.kt` to `.java` file
2. Replace some math functions with Java Math functions.
3. Put the result file in Java project.
