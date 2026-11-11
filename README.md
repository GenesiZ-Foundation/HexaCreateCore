
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/


alright, now onto the spell casting itself, when drawing spells, the shape should remain on the 3D grid, but here's the thing, before we do that, we need to find a way to lock the player in a "non-moving state", and here's what i came up with:
1) we spawn the grid around the player
2) we separate the Camera view from the player view (like a free-cam mod basically)
3) we draw the patterns in the freecam state
4) the patterns remain where they've been drawn
5) we get a box like the dialogue that shows you the current stack with 1 dialoguebox being 1 line of the stack (what would normally be in 2 is now in 3D basically)
6) the "ravensmind" dialogue box is placed at the top of the stack of dialogue boxes
7) the dialogue boxes goes from top to bottom, where we don't care if it can go through the ground (since hexcastin can hold a total of 512 values on the stack it'll be impossible to render all of them, so instead we'll do like i'd do on a web project, and we'll make the visible stack only 10 lines visible, but we'll make it scrollable)
