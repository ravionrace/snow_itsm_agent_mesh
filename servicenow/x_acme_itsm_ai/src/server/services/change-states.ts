/** change_request.state values in the default change model. */
export const STATE: Record<string, string> = {
    new: '-5', assess: '-4', authorize: '-3', scheduled: '-2', implement: '-1', review: '0', closed: '3', canceled: '4',
}
