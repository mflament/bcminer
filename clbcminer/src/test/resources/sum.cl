void debug();

/**
* c = a + b
*/
kernel void sum(global float* a, global float* b, global float* c, const unsigned int n)
{
    debug();

    int id = get_global_id(0);
    if (id < n)
        c[id] = a[id] + b[id];
}

void debug() {
    int id = get_global_id(0);
    int lid = get_local_id(0);
    int ls = get_local_size(0);
    int gid = get_group_id(0);
    int ng = get_num_groups(0);
    printf("%d %d/%d %d/%d\n", id, lid,ls, gid, ng);
}