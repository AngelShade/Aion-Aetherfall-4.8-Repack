-- Deferred options owned by Camera.
AF.Config:Register('Camera', function()
    return {{name='Camera',order=9,status='Status',hint='Applies now. Scroll to zoom out. Native restores your original value.',rows={
 {order=1,label='Field of view',k='camfov',kind='choice',format='FormatOption'},
 {order=2,label='Maximum zoom distance',k='camdist',kind='choice',format='FormatOption'}}}}
end)
