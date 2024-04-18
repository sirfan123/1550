
#include <sys/mman.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <time.h>

// Structure for semaphore
struct cs1550_sem
{
    int value;
    struct mutex *lock; // So the kernel can lock on this instance
    // Some process queue of your devising
    struct queue_node *queue_head; // Process queue head
    struct queue_node *queue_tail; // Process queue tail
};

// System calls
#define SYS_CS1550_SEMINIT 441
#define SYS_CS1550_DOWN 442
#define SYS_CS1550_UP 443

void init(struct cs1550_sem *sem, int value)
{
    syscall(441, sem, value);
}
void down(struct cs1550_sem *sem)
{
    syscall(442, sem);
}
void up(struct cs1550_sem *sem)
{
    syscall(443, sem);
}

void producerN(struct cs1550_sem *empty, struct cs1550_sem *full, struct cs1550_sem *mutex, struct cs1550_sem *cum_sem, char *direction, int *count, int *total_cars, int *buffer,
               int *in, char *honked, time_t *start_time, int buffer_size)
{
    while (1)
    {
        down(empty);
        down(mutex);
        // Increment the total number of cars that have passed through the intersection
        *total_cars = *total_cars + 1;
        // Store the car number in the buffer
        buffer[*in] = *total_cars;
        // Increment which spot in the buffer we are producing to
        (*in) = (*in + 1) % buffer_size;
        (*count)++;
        // Check to see if the car needs to honk
        if (*honked == 'N')
        {
            *honked = 'Y';
            *direction = 'N'; // Start consuming from here
            printf("Car %d coming from the %c direction, blew their horn at time %d.\n", *total_cars, *direction, (int)(time(NULL) - *start_time));
            printf("\n");
            printf("The flagperson is now awake.\n");
            printf("\n");
        }
        // Car actually arrived in the buffer
        printf("Car %d coming from the N direction arrived in the queue at time %ld.\n", *total_cars, (time(NULL) - *start_time));
        printf("\n");
        up(mutex);

        up(full);
        up(cum_sem); // Cum sem is used to sleep naturally when both queues are 0
        // If no more cars are coming, we sleep
        if (rand() % 100 > 75)
        {
            sleep(8);
        }
    }
}
// Same logic as the producerN except for the south now
void producerS(struct cs1550_sem *empty, struct cs1550_sem *full, struct cs1550_sem *mutex, struct cs1550_sem *cum_sem, char *direction, int *count, int *total_cars, int *buffer,
               int *in, char *honked, time_t *start_time, int buffer_size)
{
    while (1)
    {
        down(empty);
        down(mutex);
        // Increment the total number of cars that have passed through the intersection
        *total_cars = *total_cars + 1;
        // Store the car number in the buffer
        buffer[*in] = *total_cars;
        // Increment which spot in the buffer we are producing to
        (*in) = (*in + 1) % buffer_size;
        (*count)++;
        // Check to see if the car needs to honk
        if (*honked == 'N')
        {
            *honked = 'Y';
            *direction = 'S'; // Start consuming from here
            printf("Car %d coming from the %c direction, blew their horn at time %d.\n", *total_cars, *direction, (int)(time(NULL) - *start_time));
            printf("\n");
            printf("The flagperson is now awake.\n");
            printf("\n");
        }
        printf("Car %d coming from the S direction arrived in the queue at time %ld.\n", *total_cars, (time(NULL) - *start_time));
        printf("\n");
        up(mutex);
        up(full);
        up(cum_sem); // Same cum sem as before its tracking both queues
        // If no more cars are coming, we sleep
        if (rand() % 100 > 75)
        {
            sleep(8);
        }
    }
}

void flagperson(struct cs1550_sem *empty_n, struct cs1550_sem *full_n, struct cs1550_sem *empty_s, struct cs1550_sem *full_s,
                struct cs1550_sem *mutex, struct cs1550_sem *cum_sem, int *count_n, int *count_s, int *n_buff_ptr,
                int *s_buff_ptr, int *n_out, int *s_out, int buffer_size, char *honked, char *direction, time_t *start_time)
{
    while (1)
    {
        int car_num; // The car that is being consumed actual num
        down(mutex);
        // If both queues are empty we need to print we are going to sleep
        if (*count_n == 0 && *count_s == 0)
        {
            printf("The flagperson is now asleep.\n");
            printf("\n");
            *honked = 'N'; // Need to wake him up now
        }
        up(mutex);
        // Sleep for real now with our cum_sem
        down(cum_sem);
        down(mutex);

        // Determine which buffer to consume
        if ((*count_n == 0 && *direction == 'N') || (*count_s == 0 && *direction == 'S') || (*direction == 'N' && *count_s >= 8) || (*direction == 'S' && *count_n >= 8))
        {
            // Switch direction if the current direction's queue is empty,
            // or the other direction's queue has reached the >=8
            // NOTE: this means consumer will have to consume back and forth if both are >= 8 which i believe is the requirments instead of sticking to one direction in this case
            if (*direction == 'N')
            {
                *direction = 'S';
            }
            else
            {
                *direction = 'N';
            }
        }
        // Actual consumer logic for N
        if (*direction == 'N')
        {
            down(full_n);
            car_num = n_buff_ptr[(*n_out) % buffer_size];
            (*count_n)--;
            *n_out = *n_out + 1;
            up(mutex);
            sleep(1); // Dont ever sleep in the critial region
            printf("Car %d coming from the N direction left the construction zone at time %ld.\n", car_num, (time(NULL) - *start_time));
            printf("\n");
            up(empty_n);
        }
        // Same logic for S
        else if (*direction == 'S')
        {
            down(full_s);
            car_num = s_buff_ptr[(*s_out) % buffer_size];
            (*count_s)--;
            *s_out = *s_out + 1;
            up(mutex);
            sleep(1);
            printf("Car %d coming from the S direction left the construction zone at time %ld.\n", car_num, (time(NULL) - *start_time));
            printf("\n");
            up(empty_s);
        }
    }
}

int main()
{
    // random number generator
    srand(time(NULL));

    int buffer_size = 24; // Buffer size

    // Allocate shared memory for semaphores, counters, pointers to buffers, and other variables
    void *ptr = mmap(NULL, sizeof(time_t) + sizeof(char) * 2 + sizeof(struct cs1550_sem) * 6 + sizeof(int) * 9 + buffer_size * sizeof(int) * 2, PROT_READ | PROT_WRITE, MAP_SHARED | MAP_ANONYMOUS, -1, 0);
    if (ptr == MAP_FAILED)
    {
        perror("mmap");
        exit(EXIT_FAILURE);
    }
    // Decalre everything
    time_t *start_time;         // We will use this to determine elapsed time
    int *total_cars;            // Counter for how many cars have been produced
    int *count_n;               // Count for north buffer
    int *count_s;               // Count for south buffer
    int *n_buff_ptr;            // Pointer to north buffer
    int *s_buff_ptr;            // Pointer to south buffer
    int *n_in;                  // Counter for producerN index
    int *s_in;                  // Counter for producerS index
    int *n_out;                 // Counter for consumerN index
    int *s_out;                 // Counter for consumerS index
    char *honked;               // Flag to determine if we need to wake flagperson
    char *direction;            // Flag for which direction to consume
    struct cs1550_sem *empty_n; // Semaphores
    struct cs1550_sem *full_n;
    struct cs1550_sem *empty_s;
    struct cs1550_sem *full_s;
    struct cs1550_sem *mutex;
    struct cs1550_sem *cum_sem; // This semaphore is used to sleep when both queues are empty
    // Actually mapping all of this in our shared memory
    start_time = (time_t *)ptr;
    honked = (char *)(start_time + 1);
    direction = honked + 1;
    empty_n = (struct cs1550_sem *)(direction + 1);
    full_n = empty_n + 1;
    empty_s = full_n + 1;
    full_s = empty_s + 1;
    mutex = full_s + 1;
    cum_sem = mutex + 1;
    total_cars = (int *)(cum_sem + 1);
    count_n = total_cars + 1;
    count_s = count_n + 1;
    n_in = count_s + 1;
    s_in = n_in + 1;
    n_out = s_in + 1;
    s_out = n_out + 1;
    n_buff_ptr = s_out + 1;
    s_buff_ptr = n_buff_ptr + buffer_size;

    // init everything
    init(empty_n, buffer_size);
    init(full_n, 0);
    init(empty_s, buffer_size);
    init(full_s, 0);
    init(mutex, 1);
    init(cum_sem, 0);
    *total_cars = 0;
    *direction = ' ';
    *honked = 'N';
    *count_n = 0;
    *count_s = 0;
    *n_buff_ptr = 0;
    *s_buff_ptr = 0;
    *n_in = 0;
    *n_out = 0;
    *s_in = 0;
    *s_out = 0;

    // Set our start time
    *start_time = time(NULL);
    // Init the buffer vals to 0
    for (int i = 0; i < buffer_size; i++)
    {
        n_buff_ptr[i] = 0;
        s_buff_ptr[i] = 0;
    }

    if (fork() == 0)
    { // First fork is north producer
        producerN(empty_n, full_n, mutex, cum_sem, direction, count_n, total_cars, n_buff_ptr, n_in, honked, start_time, buffer_size);
    }
    else if (fork() == 0)
    { // Second fork is south producer
        producerS(empty_s, full_s, mutex, cum_sem, direction, count_s, total_cars, s_buff_ptr, s_in, honked, start_time, buffer_size);
    }
    else
    { // Parent process is consumer
        flagperson(empty_n, full_n, empty_s, full_s, mutex, cum_sem, count_n, count_s, n_buff_ptr, s_buff_ptr, n_out, s_out, buffer_size, honked, direction, start_time);
    }
    wait(NULL);
    return 0;
}
