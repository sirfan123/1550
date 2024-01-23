// RGB_TO_COLOR(31, 0, 0));   // Red
// RGB_TO_COLOR(0, 63, 0));   // Green
// RGB_TO_COLOR(0, 0, 31));   // Blue
// RGB_TO_COLOR(31, 63, 31)); // White
#include "library.h"
int main(int argc, char **argv)
{
	clear_screen();
	init_graphics();

	char key;

	do
	{
		draw_text(210, 40, "Press 'q' to exit.", RGB_TO_COLOR(31, 63, 31));
		draw_text(210, 60, "Press 's' to draw square.", RGB_TO_COLOR(31, 0, 0));
		draw_text(210, 80, "Press 'o' to draw circle.", RGB_TO_COLOR(0, 63, 0));
		// 	//draw_circle(x, y, 20, 15);

		key = getkey();

		// draw a blue rectangle
		if (key == 's')
		{
			draw_rect(210, 100, 200, 100, RGB_TO_COLOR(31, 0, 0));
		}

		if (key == 'o')
		{ 
			draw_circle(250, 140, 30, RGB_TO_COLOR(0, 63, 0));
		}
		sleep_ms(5000);
	} while (key != 'q');

	exit_graphics();

	return 0;
}
